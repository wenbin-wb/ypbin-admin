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
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.cloud.feign.support.FeignResponses;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import java.util.Objects;
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
        if (user == null || (req.getTenantId() != null
            && !Objects.equals(req.getTenantId(), user.getTenantId()))) {
            throw new BusinessException("用户名或密码错误");
        }
        // 密码不入缓存（安全），校验走 system 直查库比对
        R<Boolean> verifyResp = permissionFeignClient.verifyPassword(user.getId(), req.getPassword());
        if (verifyResp == null || !verifyResp.isSuccess() || !Boolean.TRUE.equals(verifyResp.getData())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (UserStatusEnum.DISABLED.getCode().equals(user.getStatus())) {
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
     * 当前用户信息（从登录态读取，权限/路由由 system 服务经 Feign 提供）。
     */
    public UserInfoResp currentUserInfo() {
        LoginUser loginUser = UserContext.getLoginUser()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
        Long userId = loginUser.getId();
        UserInfoResp resp = new UserInfoResp();
        resp.setUserId(userId);
        resp.setUsername(loginUser.getUsername());
        resp.setRealName(loginUser.getNickname());
        resp.setRoles(FeignResponses.dataOrThrow(permissionFeignClient.listRoleCodes(userId),
            "获取用户角色失败"));
        resp.setPermissions(FeignResponses.dataOrThrow(permissionFeignClient.listPermissions(userId),
            "获取用户权限失败"));
        return resp;
    }

    /**
     * 当前用户权限码（system 服务经 Feign 提供）。
     */
    public List<String> currentPermissions() {
        Long userId = UserContext.getLoginUser()
            .orElseThrow(() -> new BusinessException("当前用户未登录")).getId();
        return FeignResponses.dataOrThrow(permissionFeignClient.listPermissions(userId),
            "获取用户权限失败");
    }

    /**
     * 当前用户菜单路由（system 服务经 Feign 提供，前端动态菜单依赖）。
     */
    public List<RouteResp> currentRoutes() {
        Long userId = UserContext.getLoginUser()
            .orElseThrow(() -> new BusinessException("当前用户未登录")).getId();
        return FeignResponses.dataOrThrow(permissionFeignClient.listRoutes(userId),
            "获取用户菜单失败");
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
