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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 用户类型查询口径测试。
 *
 * <p>锁定安全契约：只有显式传 {@code MINIAPP} 才查询小程序用户，其余任何输入
 * （包括 {@code PLATFORM} 与大小写/空白变体）都必须回落到后台用户，
 * 避免用户列表成为查看平台账号的入口。</p>
 *
 * @author wenbin
 * @since 2026-11-17
 */
class AdminConstantsTest {

    @Test
    @DisplayName("只有 MINIAPP 放行，其余输入一律回落后台用户")
    void resolveQueryUserTypeOnlyAllowsMiniapp() {
        assertThat(AdminConstants.resolveQueryUserType(AdminConstants.USER_TYPE_MINIAPP))
            .isEqualTo(AdminConstants.USER_TYPE_MINIAPP);
    }

    @Test
    @DisplayName("PLATFORM 与其它任意输入都回落后台用户")
    void resolveQueryUserTypeFallsBackToTenant() {
        assertThat(AdminConstants.resolveQueryUserType(null))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(AdminConstants.resolveQueryUserType(""))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(AdminConstants.resolveQueryUserType("   "))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(AdminConstants.resolveQueryUserType(AdminConstants.USER_TYPE_TENANT))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(AdminConstants.resolveQueryUserType(AdminConstants.USER_TYPE_PLATFORM))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(AdminConstants.resolveQueryUserType("miniapp"))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(AdminConstants.resolveQueryUserType(" MINIAPP "))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
        assertThat(AdminConstants.resolveQueryUserType("' OR 1=1 --"))
            .isEqualTo(AdminConstants.USER_TYPE_TENANT);
    }
}
