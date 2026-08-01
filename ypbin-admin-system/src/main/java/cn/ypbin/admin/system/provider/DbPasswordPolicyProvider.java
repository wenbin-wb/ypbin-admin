/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.security.password.policy.PasswordPolicy;
import cn.ypbin.starter.security.password.policy.PasswordPolicyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 密码策略来源：从系统参数（sys_config）读取，后台改配置即时生效。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Component
@RequiredArgsConstructor
public class DbPasswordPolicyProvider implements PasswordPolicyProvider {

    private final SysConfigService configService;

    @Override
    public PasswordPolicy getPolicy() {
        PasswordPolicy policy = new PasswordPolicy();
        policy.setMinLength(configService.getInt("PASSWORD_MIN_LENGTH", 8));
        policy.setRequireDigit(configService.getBoolean("PASSWORD_REQUIRE_DIGIT", true));
        policy.setRequireLetter(configService.getBoolean("PASSWORD_REQUIRE_LETTER", true));
        policy.setRequireSymbol(configService.getBoolean("PASSWORD_REQUIRE_SYMBOL", false));
        policy.setAllowContainUsername(configService.getBoolean("PASSWORD_ALLOW_CONTAIN_USERNAME", false));
        policy.setErrorLockCount(configService.getInt("PASSWORD_ERROR_LOCK_COUNT", 5));
        policy.setLockMinutes(configService.getInt("PASSWORD_LOCK_MINUTES", 15));
        policy.setExpirationDays(configService.getInt("PASSWORD_EXPIRATION_DAYS", 0));
        policy.setHistoryCount(configService.getInt("PASSWORD_HISTORY_COUNT", 0));
        return policy;
    }
}
