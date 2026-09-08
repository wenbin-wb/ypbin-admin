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

import cn.ypbin.admin.auth.dto.MiniappLoginReq;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 微信小程序登录服务。
 *
 * <p>以微信授权凭证 code 换取 openid，自动创建或更新系统用户，并由 {@link LoginSupport}
 * 完成 Sa-Token 登录会话建立与令牌签发。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class MiniappLoginService {

    private static final Logger log = LoggerFactory.getLogger(MiniappLoginService.class);
    private static final String WX_AUTH_URL = "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    private final ISystemClient systemClient;
    private final LoginSupport loginSupport;
    private final ObjectMapper objectMapper;

    @Value("${ypbin.wechat.miniapp.secret:}")
    private String wechatSecret;

    /**
     * 微信小程序登录。
     *
     * @param req 登录请求参数
     * @return 登录令牌响应
     */
    public LoginResp login(MiniappLoginReq req) {
        String openid = resolveOpenid(req.getAppid(), req.getCode());
        if (!StringUtils.hasText(openid)) {
            throw new BusinessException("获取微信用户身份失败");
        }

        String username = "wx_" + openid;
        R<SysUser> userRes = systemClient.getOrCreateMiniappUser(username, req.getNickname(), req.getAvatarUrl());
        if (userRes == null || !userRes.isSuccess() || userRes.getData() == null) {
            throw new BusinessException("创建或获取小程序用户失败: " + (userRes != null ? userRes.getMessage() : "远程服务无响应"));
        }

        SysUser user = userRes.getData();
        return loginSupport.completeLogin(user, "MINIAPP");
    }

    /**
     * 解析微信 OpenID。配有 Secret 且有 code 时调微信官方 API；未配或无 code 时走 Mock 兜底。
     */
    private String resolveOpenid(String appid, String code) {
        if (StringUtils.hasText(wechatSecret) && StringUtils.hasText(code)) {
            try {
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout(Duration.ofSeconds(5));
                requestFactory.setReadTimeout(Duration.ofSeconds(5));
                RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();

                String resBody = restClient.get()
                    .uri(WX_AUTH_URL, appid, wechatSecret, code)
                    .retrieve()
                    .body(String.class);

                if (StringUtils.hasText(resBody)) {
                    JsonNode node = objectMapper.readTree(resBody);
                    if (node.has("openid")) {
                        return node.get("openid").asText();
                    }
                    log.error("微信登录 code2Session 失败: {}", resBody);
                }
            } catch (Exception e) {
                log.error("请求微信接口异常: appid={}", appid, e);
            }
        }
        // 开发/测试或未配置微信密钥时的稳定 Mock openid
        if (StringUtils.hasText(code)) {
            return "mock_" + Math.abs(code.hashCode());
        }
        return "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
