/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.service;

import cn.ypbin.admin.auth.config.WechatMiniappProperties;
import cn.ypbin.admin.auth.dto.MiniappLoginReq;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;

/**
 * 微信小程序登录服务。
 *
 * <p>以微信授权凭证 code 换取 openid，自动创建或更新系统用户，并由 {@link LoginSupport}
 * 完成 Sa-Token 登录会话建立与令牌签发。</p>
 *
 * <p><b>失败一律显式抛出（fail-closed）</b>：openid 是后续所有接口的身份根，这里绝不能出现
 * 「换取失败就退化成 Mock/默认身份」的静默降级——那等于把「登录」变成任何人可自选身份的入口
 * （历史实现即为此形态：未配置 Secret 时返回 {@code mock_<code.hashCode()>}，同一 code 恒等同一
 * 账号，可被用于冒用他人身份）。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class MiniappLoginService {

    private static final Logger log = LoggerFactory.getLogger(MiniappLoginService.class);

    private static final String WX_AUTH_URL =
        "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}"
            + "&js_code={code}&grant_type=authorization_code";

    /** 端侧标识：小程序（基础契约不写死，由本端侧传入）。 */
    private static final String USER_TYPE_MINIAPP = "MINIAPP";

    /** 小程序用户首次登录且未带昵称时的默认展示名（端侧语义留在端侧）。 */
    private static final String DEFAULT_NICKNAME_MINIAPP = "微信用户";

    /** Mock 场景 openid 前缀（仅 mock-enabled=true 时使用，便于线上排查误开）。 */
    private static final String MOCK_OPENID_PREFIX = "mock_";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final ISystemClient systemClient;
    private final LoginSupport loginSupport;
    private final ObjectMapper objectMapper;
    private final WechatMiniappProperties wechatMiniappProperties;

    /**
     * 微信小程序登录。
     *
     * @param req 登录请求参数
     * @return 登录令牌响应
     */
    public LoginResp login(MiniappLoginReq req, String clientIp, @Nullable String userAgent) {
        if (StringUtils.hasText(req.getAppid())
            && StringUtils.hasText(wechatMiniappProperties.getAppid())
            && !req.getAppid().equals(wechatMiniappProperties.getAppid())) {
            // 请求方给出的 appid 与服务端配置不一致：直接拒绝，避免用错凭据换取身份
            throw new BusinessException("小程序 appid 与平台配置不一致");
        }

        String openid = resolveOpenid(req.getCode());
        String username = "wx_" + openid;
        R<SysUserDto> userRes =
            systemClient.getOrCreateUserByUsername(username, req.getNickname(), req.getAvatarUrl(),
                USER_TYPE_MINIAPP, DEFAULT_NICKNAME_MINIAPP);
        if (userRes == null || !userRes.isSuccess() || userRes.getData() == null) {
            throw new BusinessException("创建或获取小程序用户失败: "
                + (userRes != null ? userRes.getMessage() : "远程服务无响应"));
        }

        SysUserDto user = userRes.getData();
        LoginResp resp = loginSupport.completeLogin(user, USER_TYPE_MINIAPP, clientIp, userAgent);
        // OpenID 仅小程序登录可知（落库时以 wx_ 前缀存为 username，此处剥前缀还原为契约字段）
        resp.setOpenid(openid);
        return resp;
    }

    /**
     * 解析微信 OpenID。
     *
     * <p>三种去向，均为显式行为：凭据齐备→调微信官方接口（失败即抛）；凭据缺失且显式开启 Mock→
     * 记录 WARN 后返回 Mock openid；凭据缺失且未开启 Mock→抛业务异常（fail-closed）。</p>
     *
     * @param code 微信登录凭证
     * @return openid
     */
    private String resolveOpenid(@Nullable String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("微信登录凭证 code 不能为空");
        }
        if (!wechatMiniappProperties.credentialsConfigured()) {
            if (!wechatMiniappProperties.isMockEnabled()) {
                // 不给出「静默 Mock」这条后路：未配置凭据时登录必须失败，否则任何人可用任意 code
                // 换取一个稳定账号（同一 code 恒等同一身份），等同于免鉴权登录他人账号
                throw new BusinessException("微信小程序登录未配置（缺少 appid/secret），已拒绝登录");
            }
            log.warn("微信小程序登录运行在 Mock 模式（仅限本地开发；生产必须配置 appid/secret "
                + "并保持 ypbin.wechat.miniapp.mock-enabled=false）");
            return MOCK_OPENID_PREFIX + Math.abs(code.hashCode());
        }

        String resBody = callWechatSession(code);
        if (!StringUtils.hasText(resBody)) {
            throw new BusinessException("微信登录接口返回为空");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(resBody);
        } catch (JacksonException e) {
            log.error("解析微信 code2Session 响应失败: {}", resBody, e);
            throw new BusinessException("解析微信登录响应失败");
        }
        JsonNode openidNode = node.get("openid");
        if (openidNode == null || !StringUtils.hasText(openidNode.asText())) {
            // 微信侧失败原因（errcode/errmsg）必须落日志：不然线上只看到一句「登录失败」
            log.error("微信 code2Session 换取 openid 失败: {}", resBody);
            throw new BusinessException("获取微信用户身份失败");
        }
        return openidNode.asText();
    }

    /**
     * 调用微信 code2Session 接口（独立方法以便单测替换，不在测试里发真实外网请求）。
     *
     * @param code 微信登录凭证
     * @return 响应原文
     */
    String callWechatSession(String code) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        try {
            return restClient.get()
                .uri(WX_AUTH_URL, wechatMiniappProperties.getAppid(),
                    wechatMiniappProperties.getSecret(), code)
                .retrieve()
                .body(String.class);
        } catch (RuntimeException e) {
            // 抛出去由调用方转成业务异常：这里绝不回退 Mock（回退＝把网络故障变成身份伪造入口）
            log.error("请求微信 code2Session 接口异常: appid={}", wechatMiniappProperties.getAppid(), e);
            throw new BusinessException("微信登录服务暂时不可用，请稍后重试");
        }
    }
}
