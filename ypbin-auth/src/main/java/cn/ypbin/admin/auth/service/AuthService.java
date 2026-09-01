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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务（微服务版）。
 *
 * <p>登录校验通过后由 {@link LoginSupport} 统一收尾：{@link LoginHelper#login} 建立 sa-token
 * 登录态，并把 {@link LoginUser} 写入 sa-token 会话（键 {@code UserContext.KEY_LOGIN_USER}）——
 * 网关的 {@code SaTokenGatewayAuthProvider} 从会话读取该对象签发下游身份头。</p>
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
    private final LoginSupport loginSupport;

    /**
     * 账号密码登录。
     */
    public LoginResp login(LoginReq req, String ip) {
        SysUser user = SysCache.getUserByUsername(req.getUsername());
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }
        // 密码不入缓存（安全），校验走 system 直查库比对
        Boolean matched = permissionFeignClient.verifyPassword(user.getId(), req.getPassword()).getData();
        if (!Boolean.TRUE.equals(matched)) {
            throw new BusinessException("用户名或密码错误");
        }
        if (cn.ypbin.admin.system.enums.UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }
        return loginSupport.completeLogin(user, "ACCOUNT");
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
