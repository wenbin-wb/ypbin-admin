/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 一次性平台管理员初始化配置。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@ConfigurationProperties(prefix = SecurityBootstrapProperties.PREFIX)
public class SecurityBootstrapProperties {

    public static final String PREFIX = "ypbin.admin.bootstrap";

    private boolean enabled;
    private String username;
    private String password;
    private String realName = "平台管理员";
    private Long tenantId = 1L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
