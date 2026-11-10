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
 * <p><strong>为什么放在 common 而不是 auth 或 system</strong>：注册开关由 auth（匿名入口）读取、
 * 默认租户与默认角色由 system（落库侧）读取，两个服务各有一份常量就会出现「改一处漏一处、
 * 开关静默失效」。common 是两个服务共同依赖的唯一位置，故与 {@link AdminConstants} 并列。</p>
 *
 * <p>注意：这些是 {@code sys_config} 的业务参数键，<b>不是</b> Spring
 * {@code @ConfigurationProperties} 前缀。键值的初始来源是 system 侧的
 * {@code deploy/sql/002-data.sql}（全新安装）与 {@code deploy/sql/migration/}（增量升级）。</p>
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
