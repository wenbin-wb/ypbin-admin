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

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.starter.core.exception.BusinessException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统参数读取门面（auth 域）。
 *
 * <p>auth 域需要按参数键读取 system-svc 维护的 sys_config（登录开关、短信验证码 TTL 等），
 * 统一经本类走 Feign 读取，避免各业务类散落 RPC 与解析逻辑。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Component
@RequiredArgsConstructor
public class AuthConfigReader {

    /** 布尔真值集合 */
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "on", "yes");

    private final ISystemClient systemClient;

    /**
     * 取字符串参数。
     *
     * @param key          参数键
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public String getString(String key, String defaultValue) {
        ConfigValue value = SysCache.getConfigByKey(key);
        if (value == null || value.getConfigValue() == null) {
            return defaultValue;
        }
        return value.getConfigValue();
    }

    /**
     * 取布尔参数（"true"/"1"/"on"/"yes" 视为真）。
     *
     * @param key          参数键
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        return value == null ? defaultValue : TRUE_VALUES.contains(value.trim().toLowerCase());
    }

    /**
     * 取整数参数。
     *
     * @param key          参数键
     * @param defaultValue 缺省值
     * @return 参数值
     */
    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("系统参数必须为整数：" + key);
        }
    }
}
