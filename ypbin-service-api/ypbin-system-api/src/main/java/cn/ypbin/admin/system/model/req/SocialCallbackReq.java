/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.req;

import lombok.Getter;
import lombok.Setter;
/**
 * 第三方登录回调请求。
 *
 * <p>共享类：复制自单体版 ypbin-admin-system，已归位至 api 模块，作为跨服务共享契约。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Getter
@Setter
public class SocialCallbackReq {

    /** 授权码（GitHub/Gitee/QQ/微信等平台） */
    private String code;

    /** 授权码（支付宝等平台） */
    private String auth_code;

    /** 授权跳转时携带的 state 回传值 */
    private String state;
}
