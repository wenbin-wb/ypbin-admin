/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.constant;

/**
 * 强制修改密码相关的会话键与取值。
 *
 * <p>登录时把「是否必须先改密」写入 sa-token 会话，拦截器按同一键决定是否放行请求。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
public final class PwdChangeKeys {

    private PwdChangeKeys() {
    }

    /** sa-token 会话键：是否必须先修改密码（Boolean）。 */
    public static final String REQUIRED = "pwdChangeRequired";

    /** {@code sys_user.pwd_change_required}：必须改密。 */
    public static final int REQUIRED_YES = 1;

    /** {@code sys_user.pwd_change_required}：无需改密。 */
    public static final int REQUIRED_NO = 0;
}
