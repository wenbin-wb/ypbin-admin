/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.entity.SysConfig;
import cn.ypbin.admin.system.model.req.SocialConfigUpdateReq;
import cn.ypbin.admin.system.model.resp.SocialConfigResp;
import cn.ypbin.admin.system.service.SocialConfigService;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.social.SocialAuthRequestFactory;
import cn.ypbin.admin.system.social.SocialConfigChangedEvent;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 第三方登录平台配置服务实现。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Service
@RequiredArgsConstructor
public class SocialConfigServiceImpl implements SocialConfigService {

    private static final String SOCIAL_GROUP = "social";
    private static final String SOCIAL_PREFIX = "SOCIAL_";
    private static final String ENABLED_SUFFIX = "_ENABLED";
    private static final String CLIENT_ID_SUFFIX = "_CLIENT_ID";
    private static final String CLIENT_SECRET_SUFFIX = "_CLIENT_SECRET";
    private static final String REDIRECT_URI_SUFFIX = "_REDIRECT_URI";
    private static final String PUBLIC_KEY_SUFFIX = "_PUBLIC_KEY";

    /** 支付宝平台标识（需公钥配置的平台） */
    private static final String ALIPAY_SOURCE = "alipay";

    private final SysConfigService configService;
    private final SocialAuthRequestFactory requestFactory;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<SocialConfigResp> listConfigs() {
        Map<String, SysConfig> configs = loadSocialConfigs();
        return SocialAuthRequestFactory.SOURCES.stream()
            .map(source -> toResp(source, configs))
            .toList();
    }

    @Override
    public SocialConfigResp getConfig(String source) {
        String normalizedSource = normalizeSource(source);
        return toResp(normalizedSource, loadSocialConfigs());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String source, SocialConfigUpdateReq req) {
        String normalizedSource = normalizeSource(source);
        Map<String, SysConfig> configs = loadPlatformConfigs(normalizedSource);
        String prefix = keyPrefix(normalizedSource);
        String oldSecret = value(configs, prefix + CLIENT_SECRET_SUFFIX);
        String clientSecret = StringUtils.hasText(req.getClientSecret()) ? req.getClientSecret().trim() : oldSecret;
        String clientId = trimToEmpty(req.getClientId());
        String redirectUri = trimToEmpty(req.getRedirectUri());
        String publicKey = ALIPAY_SOURCE.equals(normalizedSource) ? trimToEmpty(req.getPublicKey()) : "";

        validateRedirectUri(redirectUri);
        AuthRequest request = null;
        if (Boolean.TRUE.equals(req.getEnabled())) {
            validateEnabled(normalizedSource, clientId, clientSecret, redirectUri, publicKey);
            request = createRequest(normalizedSource, clientId, clientSecret, redirectUri, publicKey);
        }

        Map<String, String> updates = new HashMap<>();
        updates.put(prefix + ENABLED_SUFFIX, req.getEnabled().toString());
        updates.put(prefix + CLIENT_ID_SUFFIX, clientId);
        updates.put(prefix + CLIENT_SECRET_SUFFIX, clientSecret);
        updates.put(prefix + REDIRECT_URI_SUFFIX, redirectUri);
        if (ALIPAY_SOURCE.equals(normalizedSource)) {
            updates.put(prefix + PUBLIC_KEY_SUFFIX, publicKey);
        }
        updates.forEach((key, configValue) -> updateRequired(configs.get(key), configValue));
        eventPublisher.publishEvent(new SocialConfigChangedEvent(
            normalizedSource, Boolean.TRUE.equals(req.getEnabled()), request));
    }

    @Override
    public AuthRequest createEnabledRequest(String source) {
        String normalizedSource = normalizeSource(source);
        Map<String, SysConfig> configs = loadPlatformConfigs(normalizedSource);
        String prefix = keyPrefix(normalizedSource);
        if (!parseEnabled(value(configs, prefix + ENABLED_SUFFIX), normalizedSource)) {
            throw new BusinessException("第三方登录平台未启用：" + normalizedSource);
        }
        String clientId = value(configs, prefix + CLIENT_ID_SUFFIX);
        String clientSecret = value(configs, prefix + CLIENT_SECRET_SUFFIX);
        String redirectUri = value(configs, prefix + REDIRECT_URI_SUFFIX);
        String publicKey = ALIPAY_SOURCE.equals(normalizedSource)
            ? value(configs, prefix + PUBLIC_KEY_SUFFIX) : "";
        validateEnabled(normalizedSource, clientId, clientSecret, redirectUri, publicKey);
        return createRequest(normalizedSource, clientId, clientSecret, redirectUri, publicKey);
    }

    private Map<String, SysConfig> loadSocialConfigs() {
        List<SysConfig> configs = configService.list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, SOCIAL_GROUP));
        Map<String, SysConfig> result = new HashMap<>();
        for (SysConfig config : configs) {
            result.put(config.getConfigKey(), config);
        }
        return result;
    }

    private Map<String, SysConfig> loadPlatformConfigs(String source) {
        String prefix = keyPrefix(source);
        Set<String> keys = platformKeys(source, prefix);
        List<SysConfig> configs = configService.list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, SOCIAL_GROUP)
            .in(SysConfig::getConfigKey, keys));
        if (configs.size() != keys.size()) {
            throw new BusinessException("第三方登录平台配置项不完整：" + source);
        }
        Map<String, SysConfig> result = new HashMap<>();
        for (SysConfig config : configs) {
            result.put(config.getConfigKey(), config);
        }
        if (!result.keySet().equals(keys)) {
            throw new BusinessException("第三方登录平台配置项异常：" + source);
        }
        return result;
    }

    private SocialConfigResp toResp(String source, Map<String, SysConfig> configs) {
        String prefix = keyPrefix(source);
        ensureKeysExist(source, configs, platformKeys(source, prefix));
        SocialConfigResp resp = new SocialConfigResp();
        resp.setSource(source);
        resp.setEnabled(parseEnabled(value(configs, prefix + ENABLED_SUFFIX), source));
        resp.setClientId(value(configs, prefix + CLIENT_ID_SUFFIX));
        resp.setClientSecretConfigured(StringUtils.hasText(value(configs, prefix + CLIENT_SECRET_SUFFIX)));
        resp.setRedirectUri(value(configs, prefix + REDIRECT_URI_SUFFIX));
        resp.setPublicKey(ALIPAY_SOURCE.equals(source) ? value(configs, prefix + PUBLIC_KEY_SUFFIX) : "");
        return resp;
    }

    private void updateRequired(SysConfig config, String configValue) {
        if (config == null) {
            throw new BusinessException("系统参数不存在");
        }
        boolean updated = configService.update(new LambdaUpdateWrapper<SysConfig>()
            .eq(SysConfig::getId, config.getId())
            .eq(SysConfig::getConfigGroup, SOCIAL_GROUP)
            .eq(SysConfig::getConfigKey, config.getConfigKey())
            .set(SysConfig::getConfigValue, configValue));
        if (!updated) {
            throw new BusinessException("系统参数更新失败：" + config.getConfigKey());
        }
    }

    private AuthRequest createRequest(String source, String clientId, String clientSecret,
                                      String redirectUri, String publicKey) {
        try {
            return requestFactory.create(source, clientId, clientSecret, redirectUri, publicKey);
        } catch (RuntimeException e) {
            throw new BusinessException("第三方登录平台配置无效：" + source + "，" + e.getMessage());
        }
    }

    private static void validateEnabled(String source, String clientId, String clientSecret,
                                        String redirectUri, String publicKey) {
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException("ClientId 不能为空：" + source);
        }
        if (!StringUtils.hasText(clientSecret)) {
            throw new BusinessException("ClientSecret 不能为空：" + source);
        }
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException("回调地址不能为空：" + source);
        }
        validateRedirectUri(redirectUri);
        if (ALIPAY_SOURCE.equals(source) && !StringUtils.hasText(publicKey)) {
            throw new BusinessException("支付宝公钥不能为空");
        }
    }

    private static void validateRedirectUri(String redirectUri) {
        if (!StringUtils.hasText(redirectUri)) {
            return;
        }
        try {
            URI uri = new URI(redirectUri);
            String scheme = uri.getScheme();
            if ((scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)))
                || !StringUtils.hasText(uri.getHost())) {
                throw new BusinessException("回调地址仅支持有效的 http/https URI");
            }
        } catch (URISyntaxException e) {
            throw new BusinessException("回调地址不是有效 URI");
        }
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
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (!SocialAuthRequestFactory.SOURCES.contains(normalized)) {
            throw new BusinessException("不支持的第三方登录平台：" + source);
        }
        return normalized;
    }

    private static String keyPrefix(String source) {
        return SOCIAL_PREFIX + source.toUpperCase(Locale.ROOT);
    }

    private static Set<String> platformKeys(String source, String prefix) {
        if (ALIPAY_SOURCE.equals(source)) {
            return Set.of(prefix + ENABLED_SUFFIX, prefix + CLIENT_ID_SUFFIX, prefix + CLIENT_SECRET_SUFFIX,
                prefix + REDIRECT_URI_SUFFIX, prefix + PUBLIC_KEY_SUFFIX);
        }
        return Set.of(prefix + ENABLED_SUFFIX, prefix + CLIENT_ID_SUFFIX,
            prefix + CLIENT_SECRET_SUFFIX, prefix + REDIRECT_URI_SUFFIX);
    }

    private static void ensureKeysExist(String source, Map<String, SysConfig> configs, Set<String> keys) {
        if (!configs.keySet().containsAll(keys)) {
            throw new BusinessException("第三方登录平台配置项不完整：" + source);
        }
    }

    private static String value(Map<String, SysConfig> configs, String key) {
        SysConfig config = configs.get(key);
        if (config == null) {
            throw new BusinessException("系统参数不存在：" + key);
        }
        return trimToEmpty(config.getConfigValue());
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
