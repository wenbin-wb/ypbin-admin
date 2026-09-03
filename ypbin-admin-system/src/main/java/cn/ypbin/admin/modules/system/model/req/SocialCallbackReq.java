/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.req;

import lombok.Getter;
import lombok.Setter;
/**
 * 第三方登录回调请求。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Getter
@Setter
public class SocialCallbackReq {

    private String code;

    private String auth_code;

    private String state;
}
