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
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.online.OnlineUserHelper;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 登录收尾统一处理（微服务版）。
 *
 * <p>账号密码、手机验证码、第三方登录三种入口共用本方法：建立 sa-token 会话 → 构建并写入
 * {@link LoginUser}（含 clientId/authType，供网关签发身份头与操作日志取客户端信息）→
 * 把登录终端信息（IP/浏览器/操作系统/登录时间）写入 Token-Session → 回写最后登录时间 → 返回令牌。</p>
 *
 * <p><strong>为什么必须写终端信息：</strong>在线用户接口的 IP/归属地/浏览器/操作系统四个字段
 * <strong>只</strong>来自 {@link OnlineUserHelper#record} 写入 Token-Session 的记录
 * （starter 3.3.0 {@code DefaultOnlineUserService.java:163-170} 经 {@code OnlineUserHelper.getByToken}
 * 读取）；此前本仓无任何一处调用 {@code record}，因此这四个字段恒为空。写入方在 auth、
 * 读取方在 ypbin-system，两者共用 {@code sa-token-redis-template} 会话存储（见 {@code ypbin-common}
 * 的依赖注释），跨服务可读。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class LoginSupport {

    private static final Logger log = LoggerFactory.getLogger(LoginSupport.class);

    private final ISystemClient systemClient;

    private final LoginEventTracker loginEventTracker;

    /**
     * 完成登录并返回令牌。
     *
     * @param user      已校验通过的用户
     * @param authType  认证方式（ACCOUNT/PHONE/SOCIAL）
     * @param ip        客户端 IP
     * @param userAgent 客户端 User-Agent 原始串，可空
     * @return 登录结果
     */
    public LoginResp completeLogin(SysUser user, String authType, String ip, @Nullable String userAgent) {
        LoginHelper.login(user.getId(), AdminConstants.CLIENT_WEB_ADMIN, authType);
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setNickname(user.getRealName());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setClientId(AdminConstants.CLIENT_WEB_ADMIN);
        loginUser.setClientType("WEB");
        loginUser.setAuthType(authType);
        // 角色码走 SysCache 缓存（system 侧角色变更时主动清缓存）
        try {
            List<String> roles = SysCache.getUserRoleCodes(user.getId());
            if (!roles.isEmpty()) {
                loginUser.setRoles(new HashSet<>(roles));
            }
        } catch (RuntimeException e) {
            // system-svc 不可用时降级为空角色（登录不受阻，权限由网关身份头 X-Roles 决定）
            log.error("登录时获取角色码失败，userId={}", user.getId(), e);
        }
        // 登录态写入 sa-token 会话（网关从会话读身份信息签发身份头）
        StpUtil.getSession().set(UserContext.KEY_LOGIN_USER, loginUser);
        recordLoginTerminal(ip, userAgent);
        updateLastLoginTime(user.getId());
        // 先取令牌再上报：埋点真出问题时（如取令牌本身失败）不会先产出一条「登录成功」事件，
        // 事件与业务结果保持同源；上报侧自身已保证不抛（见 LoginEventTracker）
        LoginResp resp = new LoginResp(LoginHelper.getTokenValue());
        loginEventTracker.recordLogin(user, authType, ip, userAgent);
        return resp;
    }

    /**
     * 记录登录终端信息（在线用户列表的 IP/浏览器/操作系统来源）。
     *
     * <p>与 {@link #updateLastLoginTime} 同策略：终端信息属展示型附加数据，写失败不应阻断登录成功，
     * 但必须记录完整堆栈暴露问题（不静默吞掉）。归属地未接入离线 IP 库，交由读取侧留空，不在此臆造。</p>
     *
     * @param ip        客户端 IP
     * @param userAgent 客户端 User-Agent 原始串，可空
     */
    private void recordLoginTerminal(String ip, @Nullable String userAgent) {
        UserAgent ua = parseUserAgent(userAgent);
        try {
            OnlineUserHelper.record(ip,
                ua == null || ua.getBrowser() == null ? null : formatBrowser(ua),
                ua == null || ua.getOs() == null ? null : ua.getOs().getName());
        } catch (RuntimeException e) {
            log.error("登录后记录终端信息失败，在线用户的 IP/浏览器/操作系统将为空，ip={}", ip, e);
        }
    }

    @Nullable
    private UserAgent parseUserAgent(@Nullable String userAgent) {
        return userAgent == null || userAgent.isBlank() ? null : UserAgentUtil.parse(userAgent);
    }

    /**
     * 浏览器「名称 + 版本」格式化，与 starter 操作日志的写法保持一致（版本缺失只留名称）。
     */
    private String formatBrowser(UserAgent ua) {
        String name = ua.getBrowser().getName();
        String version = ua.getVersion();
        if (version == null || version.isBlank() || "Unknown".equalsIgnoreCase(version)) {
            return name;
        }
        return name + " " + version;
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
