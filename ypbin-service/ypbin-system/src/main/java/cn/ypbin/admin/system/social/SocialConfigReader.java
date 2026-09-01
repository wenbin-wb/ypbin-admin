/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.social;

import cn.ypbin.admin.system.entity.SysConfig;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 第三方登录平台配置读取（内部 Feign 契约侧）。
 *
 * <p>从系统参数（sys_config 的 SOCIAL_* 键）组装含密钥明文的 {@link SocialAuthConfig}，
 * 供 auth-svc 经内部端点拉取后构建 JustAuth 授权请求。密钥明文仅限内部传递。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
@RequiredArgsConstructor
public class SocialConfigReader {

    /** 平台配置分组 */
    private static final String SOCIAL_GROUP = "social";

    /** 配置键前缀 */
    private static final String SOCIAL_PREFIX = "SOCIAL_";

    /** 启用后缀 */
    private static final String ENABLED_SUFFIX = "_ENABLED";

    /** ClientId 后缀 */
    private static final String CLIENT_ID_SUFFIX = "_CLIENT_ID";

    /** ClientSecret 后缀 */
    private static final String CLIENT_SECRET_SUFFIX = "_CLIENT_SECRET";

    /** 回调地址后缀 */
    private static final String REDIRECT_URI_SUFFIX = "_REDIRECT_URI";

    /** 公钥后缀（支付宝专用） */
    private static final String PUBLIC_KEY_SUFFIX = "_PUBLIC_KEY";

    /** 支付宝平台标识（需公钥配置的平台） */
    private static final String ALIPAY_SOURCE = "alipay";

    private final SysConfigService configService;

    /**
     * 读取指定平台的授权配置（含密钥明文）。
     *
     * @param source 平台标识
     * @return 授权配置
     */
    public SocialAuthConfig read(String source) {
        String normalizedSource = normalizeSource(source);
        List<SysConfig> configs = loadPlatformConfigs(normalizedSource);
        String prefix = keyPrefix(normalizedSource);
        SocialAuthConfig config = new SocialAuthConfig();
        config.setSource(normalizedSource);
        config.setEnabled(parseEnabled(value(configs, prefix + ENABLED_SUFFIX), normalizedSource));
        config.setClientId(value(configs, prefix + CLIENT_ID_SUFFIX));
        config.setClientSecret(value(configs, prefix + CLIENT_SECRET_SUFFIX));
        config.setRedirectUri(value(configs, prefix + REDIRECT_URI_SUFFIX));
        config.setPublicKey(ALIPAY_SOURCE.equals(normalizedSource)
            ? value(configs, prefix + PUBLIC_KEY_SUFFIX) : "");
        return config;
    }

    /**
     * 读取全部已启用平台的授权配置（不含密钥的平台标识集合另见 {@link #enabledSources()}）。
     *
     * @return 已启用平台的授权配置列表，无启用平台时返回空集合
     */
    public List<SocialAuthConfig> listEnabled() {
        return enabledSources().stream().map(this::read).toList();
    }

    /**
     * 读取全部已启用平台的标识。
     *
     * @return 平台标识列表
     */
    public List<String> enabledSources() {
        List<SysConfig> configs = configService.list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, SOCIAL_GROUP));
        return configs.stream()
            .filter(config -> config.getConfigKey() != null
                && config.getConfigKey().endsWith(ENABLED_SUFFIX))
            .filter(config -> parseEnabledQuietly(config.getConfigValue()))
            .map(config -> sourceOf(config.getConfigKey()))
            .filter(StringUtils::hasText)
            .toList();
    }

    private List<SysConfig> loadPlatformConfigs(String source) {
        String prefix = keyPrefix(source);
        List<SysConfig> configs = configService.list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, SOCIAL_GROUP)
            .likeRight(SysConfig::getConfigKey, prefix));
        if (configs.isEmpty()) {
            throw new BusinessException("第三方登录平台配置不存在：" + source);
        }
        return configs;
    }

    private static String sourceOf(String configKey) {
        String body = configKey.substring(SOCIAL_PREFIX.length());
        return body.endsWith(ENABLED_SUFFIX)
            ? body.substring(0, body.length() - ENABLED_SUFFIX.length()).toLowerCase(Locale.ROOT)
            : "";
    }

    private static boolean parseEnabledQuietly(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static boolean parseEnabled(String enabled, String source) {
        if ("true".equalsIgnoreCase(enabled)) {
            return true;
        }
        if ("false".equalsIgnoreCase(enabled)) {
            return false;
        }
        throw new BusinessException("第三方登录启用配置必须为 true 或 false：" + source);
    }

    private static String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new BusinessException("第三方登录平台不能为空");
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private static String keyPrefix(String source) {
        return SOCIAL_PREFIX + source.toUpperCase(Locale.ROOT);
    }

    private static String value(List<SysConfig> configs, String key) {
        for (SysConfig config : configs) {
            if (key.equals(config.getConfigKey())) {
                return config.getConfigValue() == null ? "" : config.getConfigValue().trim();
            }
        }
        return "";
    }
}
