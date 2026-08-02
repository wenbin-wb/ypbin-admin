/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.auth;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.common.provider.Ip2regionLocationResolver;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.security.client.LoginClient;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.online.OnlineUserHelper;
import cn.ypbin.starter.security.online.OnlineUserHelper.Terminal;
import cn.ypbin.starter.tools.support.RequestUtils;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 登录收尾统一处理：按客户端策略登录 → 构建并写入 LoginUser 上下文（含 clientId/clientType/authType）
 * → 记录最后登录时间与在线终端信息（IP/归属地/浏览器/OS）→ 返回令牌。
 *
 * <p>账号密码、手机验证码、第三方登录三种入口共用本方法，避免各自构建 LoginUser 时字段遗漏（如漏填
 * clientType 导致操作日志取不到客户端信息）。</p>
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
     * @param authType 认证方式（ACCOUNT/PHONE/EMAIL/SOCIAL）
     * @return 登录结果
     */
    public LoginResp completeLogin(SysUser user, String authType) {
        LoginClient client = LoginHelper.login(user.getId(), AdminConstants.CLIENT_WEB_ADMIN, authType);
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setNickname(user.getRealName());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setRoles(new HashSet<>(permissionService.listRoleCodes(user.getId())));
        loginUser.setClientId(client.getClientId());
        loginUser.setClientType(client.getClientType());
        loginUser.setAuthType(authType);
        UserContext.setLoginUser(loginUser);
        userService.updateLastLoginTime(user.getId());
        recordTerminal();
        return new LoginResp(LoginHelper.getTokenValue());
    }

    /**
     * 记录在线用户终端信息（IP/归属地/浏览器/OS），供在线用户列表展示。
     */
    private void recordTerminal() {
        String ip = RequestUtils.getClientIp();
        Terminal terminal = new Terminal();
        terminal.setIp(ip);
        terminal.setLocation(locationResolver.resolve(ip));
        terminal.setBrowser(extractBrowser());
        terminal.setOs(extractOs());
        OnlineUserHelper.record(terminal);
    }

    private String extractBrowser() {
        UserAgent ua = parseUa();
        if (ua == null || ua.getBrowser() == null) {
            return "unknown";
        }
        return ua.getBrowser().getName() + (ua.getVersion() == null ? "" : " " + ua.getVersion());
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
        return UserAgentUtil.parse(ua);
    }
}
