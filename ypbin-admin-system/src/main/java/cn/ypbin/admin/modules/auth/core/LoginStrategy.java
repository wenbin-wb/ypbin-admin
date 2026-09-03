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

import cn.ypbin.admin.modules.system.model.resp.LoginResp;

/**
 * 登录策略契约。每种登录方式（账号密码/手机验证码/邮箱/社交）各自实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface LoginStrategy {

    /** 认证类型标识，如 ACCOUNT、PHONE、EMAIL */
    String authType();

    /**
     * 执行登录。
     *
     * @param req      登录请求（子类类型）
     * @param clientIp 客户端 IP
     * @return 登录结果
     */
    LoginResp login(Object req, String clientIp);
}
