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
import cn.ypbin.admin.auth.dto.LoginReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.model.resp.UserInfoResp;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务（微服务版）。
 *
 * <p>登录校验通过后：{@link LoginHelper#login} 建立 sa-token 登录态，并把 {@link LoginUser}
 * 写入 sa-token 会话（键 {@code UserContext.KEY_LOGIN_USER}）——网关的
 * {@code SaTokenGatewayAuthProvider} 从会话读取该对象签发下游身份头。</p>
 *
 * <p>权限/菜单/路由由 system-svc 提供（M3 以 Feign 打通），本服务暂返回空集合。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final ISystemClient permissionFeignClient;

    /**
     * 账号密码登录。
     */
    public LoginResp login(LoginReq req, String ip) {
        SysUser user = SysCache.getUserByUsername(req.getUsername());
        if (user == null || !PasswordEncoderUtil.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (cn.ypbin.admin.system.enums.UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        return completeLogin(user);
    }

    /**
     * 登录收尾：建立 sa-token 会话 + 写入 LoginUser 供网关读取。
     */
    private LoginResp completeLogin(SysUser user) {
        LoginHelper.login(user.getId());
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername());
        loginUser.setNickname(user.getRealName());
        loginUser.setTenantId(user.getTenantId());
        loginUser.setDeptId(user.getDeptId());
        // 角色码经 Feign 从 system-svc 获取（内部调用身份头自动透传）
        try {
            R<List<String>> roles = permissionFeignClient.listRoleCodes(user.getId());
            if (roles != null && roles.getData() != null) {
                loginUser.setRoles(new java.util.HashSet<>(roles.getData()));
            }
        } catch (Exception e) {
            // system-svc 不可用时降级为空角色（登录不受阻，权限由网关身份头 X-Roles 决定）
        }
        // 登录态写入 sa-token 会话（网关从会话读身份信息签发身份头）
        StpUtil.getSession().set(UserContext.KEY_LOGIN_USER, loginUser);
        return new LoginResp(LoginHelper.getTokenValue());
    }

    /**
     * 退出登录。
     */
    public void logout() {
        LoginHelper.logout();
    }

    /**
     * 当前用户信息（从登录态读取，权限列表由 system-svc 提供，暂空）。
     */
    public UserInfoResp currentUserInfo() {
        LoginUser loginUser = UserContext.getLoginUser()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
        UserInfoResp resp = new UserInfoResp();
        resp.setUserId(loginUser.getId());
        resp.setUsername(loginUser.getUsername());
        resp.setRealName(loginUser.getNickname());
        resp.setRoles(List.of());
        resp.setPermissions(List.of());
        return resp;
    }

    /**
     * 当前用户权限码（M3 由 system-svc 提供，暂空）。
     */
    public List<String> currentPermissions() {
        return List.of();
    }

    /**
     * 当前用户菜单路由（M3 由 system-svc 提供，暂空）。
     */
    public List<RouteResp> currentRoutes() {
        return List.of();
    }

    /**
     * 登录态是否有效（供网关健康检查与调试）。
     */
    public R<Void> checkLogin() {
        if (!LoginHelper.isLogin()) {
            throw new BusinessException("未登录");
        }
        return R.ok();
    }

}
