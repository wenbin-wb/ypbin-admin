/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.config.sms;

import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.dromara.sms4j.core.datainterface.SmsReadConfig;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.dromara.sms4j.provider.config.BaseConfig;
import org.dromara.sms4j.provider.factory.BaseProviderFactory;
import org.dromara.sms4j.provider.factory.ProviderFactoryHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 短信配置读取：从系统参数（sys_config 的 SMS_* 键）动态构建 sms4j 厂商配置。
 *
 * <p>供应商为空视为未配置（不注册实例）；配置了未知供应商直接抛异常暴露问题，避免静默失效。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
@RequiredArgsConstructor
public class SmsReadConfigDbImpl implements SmsReadConfig {

    /** 厂商键 */
    private static final String KEY_SUPPLIER = "SMS_SUPPLIER";

    /** AccessKeyId 键 */
    private static final String KEY_ACCESS_KEY_ID = "SMS_ACCESS_KEY_ID";

    /** AccessKeySecret 键 */
    private static final String KEY_ACCESS_KEY_SECRET = "SMS_ACCESS_KEY_SECRET";

    /** 短信签名键 */
    private static final String KEY_SIGNATURE = "SMS_SIGNATURE";

    /** 模板 ID 键 */
    private static final String KEY_TEMPLATE_ID = "SMS_TEMPLATE_ID";

    /** 单一厂商配置的固定配置标识 */
    public static final String CONFIG_ID = "default";

    private final SysConfigService configService;

    private final ObjectMapper objectMapper;

    @Override
    public BaseConfig getSupplierConfig(String configId) {
        return CONFIG_ID.equals(configId) ? buildConfig() : null;
    }

    @Override
    public List<BaseConfig> getSupplierConfigList() {
        BaseConfig config = buildConfig();
        return config == null ? List.of() : List.of(config);
    }

    /**
     * 刷新短信实例：先注销旧配置，再按当前数据库配置注册（未配置则保持注销）。
     */
    public void reload() {
        SmsFactory.unregister(CONFIG_ID);
        BaseConfig config = buildConfig();
        if (config != null) {
            SmsFactory.createSmsBlend(config);
        }
    }

    private BaseConfig buildConfig() {
        String supplier = configService.getString(KEY_SUPPLIER, "");
        if (!StringUtils.hasText(supplier)) {
            return null;
        }
        BaseProviderFactory<?, ?> providerFactory = ProviderFactoryHolder.requireForSupplier(supplier);
        if (providerFactory == null) {
            throw new BusinessException("未知短信厂商：" + supplier);
        }
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("configId", CONFIG_ID);
        configInfo.put("accessKeyId", configService.getString(KEY_ACCESS_KEY_ID, ""));
        configInfo.put("accessKeySecret", configService.getString(KEY_ACCESS_KEY_SECRET, ""));
        configInfo.put("signature", configService.getString(KEY_SIGNATURE, ""));
        configInfo.put("templateId", configService.getString(KEY_TEMPLATE_ID, ""));
        return (BaseConfig) objectMapper.convertValue(configInfo,
            objectMapper.getTypeFactory().constructType(providerFactory.getConfigClass()));
    }
}
