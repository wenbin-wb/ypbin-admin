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
import cn.ypbin.admin.auth.dto.LoginResp;
import cn.ypbin.admin.auth.dto.RouteResp;
import cn.ypbin.admin.auth.dto.UserInfoResp;
import cn.ypbin.admin.auth.entity.SysUser;
import cn.ypbin.admin.auth.mapper.SysUserMapper;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    private final SysUserMapper userMapper;

    /**
     * 账号密码登录。
     */
    public LoginResp login(LoginReq req, String ip) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, req.getUsername()), false);
        if (user == null || !PasswordEncoderUtil.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
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

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
