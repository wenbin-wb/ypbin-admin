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
 * 用户注册相关<b>系统参数键</b>唯一定义处（{@code sys_config.config_key}）。
 *
 * <p>注册开关、归属租户与默认角色是三处（auth 入口、system 落库、初始化 SQL）共同依赖的契约，
 * 收在一处避免「改一处漏一处、开关静默失效」。</p>
 *
 * <p>注意：这些是 {@code sys_config} 的业务参数键，<b>不是</b> Spring
 * {@code @ConfigurationProperties} 前缀。初始来源是 Flyway {@code V2__data.sql}（全新库）
 * 与 {@code V5__user_register.sql}（已升级库）。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
public final class RegisterConfigKeys {

    /** 用户自助注册开关（默认关闭；开启后才允许匿名注册） */
    public static final String REGISTER_ENABLED = "REGISTER_ENABLED";

    /** 注册用户归属的默认租户 ID（必填；注册入口不接受调用方指定租户） */
    public static final String REGISTER_DEFAULT_TENANT_ID = "REGISTER_DEFAULT_TENANT_ID";

    /** 注册用户自动绑定的默认角色标识（必填；须为该租户下的启用租户角色） */
    public static final String REGISTER_DEFAULT_ROLE_CODE = "REGISTER_DEFAULT_ROLE_CODE";

    private RegisterConfigKeys() {
    }
}
