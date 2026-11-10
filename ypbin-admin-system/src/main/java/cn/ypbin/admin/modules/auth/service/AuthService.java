/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.auth.service;

import cn.ypbin.admin.modules.system.model.req.LoginReq;
import cn.ypbin.admin.modules.system.model.req.RegisterReq;
import cn.ypbin.admin.modules.system.model.resp.LoginResp;
import cn.ypbin.admin.modules.system.model.resp.RouteResp;
import cn.ypbin.admin.modules.system.model.resp.UserInfoResp;
import java.util.List;

/**
 * 认证服务：登录、登出、当前用户信息、权限码、路由。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface AuthService {

    /**
     * 账号密码登录。
     *
     * @param req 登录请求
     * @param ip  客户端 IP（用于错误锁定维度）
     * @return 登录结果（含 accessToken）
     */
    LoginResp login(LoginReq req, String ip);

    /**
     * 用户自助注册（公开入口，开关见系统参数 {@code REGISTER_ENABLED}）。
     *
     * <p>注册成功即建立登录态并返回令牌，与登录接口同一响应契约。开关关闭、验证码未通过、
     * 用户名/手机号重复、密码不符合策略、默认租户或默认角色不可用时均返回明确的业务错误。</p>
     *
     * @param req 注册请求
     * @param ip  客户端 IP（限频维度）
     * @return 登录结果（含 accessToken）
     */
    LoginResp register(RegisterReq req, String ip);

    /**
     * 当前会话登出。
     */
    void logout();

    /**
     * 获取当前登录用户信息。
     *
     * @return 用户信息
     */
    UserInfoResp currentUserInfo();

    /**
     * 获取当前登录用户的权限码集合。
     *
     * @return 权限码列表
     */
    List<String> currentPermissions();

    /**
     * 获取当前登录用户的客户端路由树。
     *
     * @return 路由树
     */
    List<RouteResp> currentRoutes();
}
