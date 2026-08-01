/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.model.req.LoginReq;
import cn.ypbin.admin.system.model.resp.LoginResp;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.model.resp.UserInfoResp;
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
     * 获取当前登录用户的前端路由树。
     *
     * @return 路由树
     */
    List<RouteResp> currentRoutes();
}
