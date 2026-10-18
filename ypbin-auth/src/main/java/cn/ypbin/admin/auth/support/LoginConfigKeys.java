/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.support;

/**
 * 登录相关<b>系统参数键</b>唯一定义处（{@code sys_config.config_key}）。
 *
 * <p><strong>为什么收成一处</strong>：这些键此前在 auth 的多个类里各自 {@code private static final} 重复定义
 * （验证码开关在 {@code CaptchaController} 与 {@code AuthService} 各一份、短信登录开关在
 * {@code SmsLoginController} 与 {@code PhoneLoginStrategy} 各一份）。配置键是<b>跨类跨服务的契约</b>：
 * 改一处漏一处不会编译失败，只会让某个入口的开关静默失效——这类问题极难从现象反推。
 * 现统一到本类，多入口共用同一个常量。</p>
 *
 * <p>注意：这些是 {@code sys_config} 的业务参数键，<b>不是</b> Spring {@code @ConfigurationProperties}
 * 前缀，故不走 properties {@code PREFIX} 常量。键值的唯一来源是 system 侧的
 * {@code deploy/sql/002-data.sql} 种子数据与本类。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class LoginConfigKeys {

    /** 行为验证码开关（登录/验证码接口共用） */
    public static final String LOGIN_CAPTCHA_ENABLED = "LOGIN_CAPTCHA_ENABLED";

    /** 短信验证码登录开关（发码接口与登录策略共用） */
    public static final String LOGIN_SMS_ENABLED = "LOGIN_SMS_ENABLED";

    private LoginConfigKeys() {
    }
}
