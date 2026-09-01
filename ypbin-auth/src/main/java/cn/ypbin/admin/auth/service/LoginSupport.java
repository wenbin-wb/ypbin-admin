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

import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 登录收尾统一处理（微服务版）。
 *
 * <p>账号密码、手机验证码、第三方登录三种入口共用本方法：建立 sa-token 会话 → 构建并写入
 * {@link LoginUser}（含 clientId/authType，供网关签发身份头与操作日志取客户端信息）→
 * 回写最后登录时间 → 返回令牌。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class LoginSupport {

    private static final Logger log = LoggerFactory.getLogger(LoginSupport.class);

    private final ISystemClient systemClient;

    /**
     * 完成登录并返回令牌。
     *
     * @param user     已校验通过的用户
     * @param authType 认证方式（ACCOUNT/PHONE/SOCIAL）
     * @return 登录结果
     */
    public LoginResp completeLogin(SysUser user, String authType) {
        LoginHelper.login(user.getId(), AdminConstants.CLIENT_WEB_ADMIN, authType);
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setNickname(user.getRealName());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setClientId(AdminConstants.CLIENT_WEB_ADMIN);
        loginUser.setClientType("WEB");
        loginUser.setAuthType(authType);
        // 角色码经 Feign 从 system-svc 获取（内部调用身份头自动透传）
        try {
            R<List<String>> roles = systemClient.listRoleCodes(user.getId());
            if (roles != null && roles.getData() != null) {
                loginUser.setRoles(new HashSet<>(roles.getData()));
            }
        } catch (RuntimeException e) {
            // system-svc 不可用时降级为空角色（登录不受阻，权限由网关身份头 X-Roles 决定）
            log.error("登录时获取角色码失败，userId={}", user.getId(), e);
        }
        // 登录态写入 sa-token 会话（网关从会话读身份信息签发身份头）
        StpUtil.getSession().set(UserContext.KEY_LOGIN_USER, loginUser);
        updateLastLoginTime(user.getId());
        return new LoginResp(LoginHelper.getTokenValue());
    }

    /**
     * 回写最后登录时间（经 Feign 调 system-svc）。回写失败不影响登录成功，记录日志暴露问题。
     *
     * @param userId 用户 ID
     */
    private void updateLastLoginTime(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            systemClient.updateLastLoginTime(userId);
        } catch (RuntimeException e) {
            log.error("登录后回写最后登录时间失败，userId={}", userId, e);
        }
    }
}
