/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.core;

import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.common.constant.PwdChangeKeys;
import cn.ypbin.admin.common.provider.Ip2regionLocationResolver;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.enums.UserStatusEnum;
import cn.ypbin.admin.modules.system.model.resp.LoginResp;
import cn.ypbin.admin.modules.system.service.SysPermissionService;
import cn.ypbin.admin.modules.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.client.LoginClient;
import cn.ypbin.starter.security.client.LoginClientRequest;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.online.OnlineUserHelper;
import cn.ypbin.starter.security.online.OnlineUserHelper.Terminal;
import cn.ypbin.starter.core.util.UserAgentFormatter;
import cn.ypbin.starter.tools.support.RequestUtils;
import cn.hutool.http.useragent.UserAgent;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 登录收尾统一处理：按客户端策略登录 → 构建并写入 LoginUser 上下文（含 clientId/clientType/authType）
 * → 记录最后登录时间与在线终端信息（IP/归属地/浏览器/OS）→ 返回令牌。
 *
 * <p>账号密码、手机验证码、第三方登录、微信小程序四种入口共用本方法，避免各自构建 LoginUser 时字段遗漏
 * （如漏填 clientType 导致操作日志取不到客户端信息）。客户端身份由请求头声明（见
 * {@code AdminConstants.CLIENT_ID_HEADER}），未声明时回落到配置的默认客户端——新增应用不需要改后端代码；
 * 配置了客户端密钥的端还需带 {@code AdminConstants.CLIENT_SECRET_HEADER}，未配置密钥的端不需要。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class LoginSupport {

    private final SysUserService userService;
    private final SysPermissionService permissionService;
    private final Ip2regionLocationResolver locationResolver;

    /**
     * 完成登录并返回令牌。
     *
     * @param user     已校验通过的用户
     * @param authType 认证方式（ACCOUNT/PHONE/EMAIL/SOCIAL/MINIAPP）
     * @return 登录结果
     */
    public LoginResp completeLogin(SysUser user, String authType) {
        // 禁用校验放在唯一收尾处：各入口容易漏掉，导致被禁用用户换一条链路即可继续登录
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        LoginClientRequest clientRequest = buildClientRequest(authType, RequestUtils.getRequest());
        LoginClient client = LoginHelper.login(user.getId(), clientRequest);
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setNickname(user.getRealName());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoles(new HashSet<>(permissionService.listRoleCodes(user.getId())));
        loginUser.setClientId(client.getClientId());
        loginUser.setClientType(client.getClientType());
        loginUser.setAuthType(authType);
        UserContext.setLoginUser(loginUser);
        boolean mustChangePassword = Integer.valueOf(PwdChangeKeys.REQUIRED_YES)
            .equals(user.getPwdChangeRequired());
        StpUtil.getSession().set(PwdChangeKeys.REQUIRED, mustChangePassword);
        userService.updateLastLoginTime(user.getId());
        recordTerminal();
        return new LoginResp(LoginHelper.getTokenValue(), mustChangePassword);
    }

    /**
     * 从请求头解析客户端 ID。
     *
     * <p>客户端只声明「我是谁」，具体策略（Token 有效期、多端登录、认证方式白名单）仍由服务端
     * {@code sys_client} 决定。未声明时返回 {@code null}，由 starter 回落到配置的默认客户端。</p>
     *
     * @param request 当前请求；无 Web 上下文时为 {@code null}
     * @return 客户端 ID；未声明时为空
     */
    @Nullable
    static String resolveClientId(@Nullable HttpServletRequest request) {
        return resolveHeader(request, AdminConstants.CLIENT_ID_HEADER);
    }

    /**
     * 组装登录客户端请求：身份与密钥都取自请求头。
     *
     * <p>客户端 ID 缺失时由 starter 回落默认客户端；密钥只在客户端配置了密钥时才由 starter 校验，
     * 未配置的端不传该头。</p>
     *
     * @param authType 认证方式
     * @param request  当前请求；无 Web 上下文时为 {@code null}
     * @return 登录客户端请求
     */
    static LoginClientRequest buildClientRequest(String authType, @Nullable HttpServletRequest request) {
        LoginClientRequest clientRequest = new LoginClientRequest(resolveClientId(request), authType);
        clientRequest.setClientSecret(resolveClientSecret(request));
        return clientRequest;
    }

    /**
     * 从请求头解析客户端密钥。
     *
     * <p>只有配置了密钥的客户端才需要提供；浏览器、小程序等无法保管密钥的端应保持客户端密钥为空。</p>
     *
     * @param request 当前请求；无 Web 上下文时为 {@code null}
     * @return 客户端密钥；未声明时为空
     */
    @Nullable
    static String resolveClientSecret(@Nullable HttpServletRequest request) {
        return resolveHeader(request, AdminConstants.CLIENT_SECRET_HEADER);
    }

    /**
     * 读取请求头并去掉首尾空白。
     *
     * @param request 当前请求；无 Web 上下文时为 {@code null}
     * @param name    头名称
     * @return 头值；缺失或空白时为空
     */
    @Nullable
    private static String resolveHeader(@Nullable HttpServletRequest request, String name) {
        if (request == null) {
            return null;
        }
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 记录在线用户终端信息（IP/归属地/浏览器/OS），供在线用户列表展示。
     */
    private void recordTerminal() {
        // 与操作日志、限流、埋点同一口径：走可信代理解析，取真实客户端 IP。
        // 原先 getClientIp() 只取 TCP 对端，部署在 nginx 后记录的是容器网桥地址。
        String ip = RequestUtils.getClientIp(true);
        Terminal terminal = new Terminal();
        terminal.setIp(ip);
        terminal.setLocation(locationResolver.resolve(ip));
        terminal.setBrowser(extractBrowser());
        terminal.setOs(extractOs());
        OnlineUserHelper.record(terminal);
    }

    private String extractBrowser() {
        // 口径统一由 starter 的 UserAgentFormatter 提供（与操作日志共用同一实现）
        String browser = UserAgentFormatter.formatBrowser(parseUa());
        return browser == null ? "unknown" : browser;
    }

    private String extractOs() {
        UserAgent ua = parseUa();
        return (ua == null || ua.getOs() == null) ? "unknown" : ua.getOs().getName();
    }

    private UserAgent parseUa() {
        String ua = RequestUtils.getUserAgent();
        if (ua == null || ua.isBlank() || "unknown".equals(ua)) {
            return null;
        }
        return UserAgentFormatter.parse(ua);
    }
}
