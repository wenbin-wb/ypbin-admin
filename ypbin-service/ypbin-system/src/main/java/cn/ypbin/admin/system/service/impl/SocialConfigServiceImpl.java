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

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysConfig;
import cn.ypbin.admin.system.model.req.SocialConfigUpdateReq;
import cn.ypbin.admin.system.model.resp.SocialConfigResp;
import cn.ypbin.admin.system.service.SocialConfigService;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.social.SocialAuthRequestFactory;
import cn.ypbin.admin.system.social.SocialConfigChangedEvent;
import cn.ypbin.admin.system.social.SocialConfigKeys;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
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
 * <p>配置键与解析规则统一由 {@link SocialConfigKeys} 提供，避免与内部读取侧
 * {@code SocialConfigReader} 各写一份后漂移。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Service
@RequiredArgsConstructor
public class SocialConfigServiceImpl implements SocialConfigService {

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
        String normalizedSource = SocialConfigKeys.normalizeSource(source);
        return toResp(normalizedSource, loadSocialConfigs());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(String source, SocialConfigUpdateReq req) {
        String normalizedSource = SocialConfigKeys.normalizeSource(source);
        if (!SocialAuthRequestFactory.SOURCES.contains(normalizedSource)) {
            throw new BusinessException("不支持的第三方登录平台：" + source);
        }
        if (req.getEnabled() == null) {
            throw new BusinessException("启用状态不能为空：" + normalizedSource);
        }
        Map<String, SysConfig> configs = loadPlatformConfigs(normalizedSource);
        String prefix = SocialConfigKeys.keyPrefix(normalizedSource);
        String oldSecret = value(configs, prefix + SocialConfigKeys.CLIENT_SECRET_SUFFIX);
        String clientSecret = StringUtils.hasText(req.getClientSecret())
            ? req.getClientSecret().trim() : oldSecret;
        String clientId = SocialConfigKeys.trimToEmpty(req.getClientId());
        String redirectUri = SocialConfigKeys.trimToEmpty(req.getRedirectUri());
        String publicKey = SocialConfigKeys.ALIPAY_SOURCE.equals(normalizedSource)
            ? SocialConfigKeys.trimToEmpty(req.getPublicKey()) : "";

        validateRedirectUri(redirectUri);
        AuthRequest request = null;
        if (Boolean.TRUE.equals(req.getEnabled())) {
            validateEnabled(normalizedSource, clientId, clientSecret, redirectUri, publicKey);
            request = createRequest(normalizedSource, clientId, clientSecret, redirectUri, publicKey);
        }

        Map<String, String> updates = new HashMap<>();
        updates.put(prefix + SocialConfigKeys.ENABLED_SUFFIX, req.getEnabled().toString());
        updates.put(prefix + SocialConfigKeys.CLIENT_ID_SUFFIX, clientId);
        updates.put(prefix + SocialConfigKeys.CLIENT_SECRET_SUFFIX, clientSecret);
        updates.put(prefix + SocialConfigKeys.REDIRECT_URI_SUFFIX, redirectUri);
        if (SocialConfigKeys.ALIPAY_SOURCE.equals(normalizedSource)) {
            updates.put(prefix + SocialConfigKeys.PUBLIC_KEY_SUFFIX, publicKey);
        }
        updates.forEach((key, configValue) -> updateRequired(configs.get(key), configValue));
        eventPublisher.publishEvent(new SocialConfigChangedEvent(
            normalizedSource, Boolean.TRUE.equals(req.getEnabled()), request));
        // 读侧 key 使用归一后的 source；这里必须同口径清理，否则 "GitHub" 只会清掉一个永不命中的键
        SysCache.evictSocialConfig(normalizedSource);
    }

    private Map<String, SysConfig> loadSocialConfigs() {
        List<SysConfig> configs = configService.list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, SocialConfigKeys.GROUP));
        Map<String, SysConfig> result = new HashMap<>();
        for (SysConfig config : configs) {
            result.put(config.getConfigKey(), config);
        }
        return result;
    }

    private Map<String, SysConfig> loadPlatformConfigs(String source) {
        Set<String> keys = SocialConfigKeys.platformKeys(source);
        List<SysConfig> configs = configService.list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, SocialConfigKeys.GROUP)
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
        String prefix = SocialConfigKeys.keyPrefix(source);
        ensureKeysExist(source, configs, SocialConfigKeys.platformKeys(source));
        SocialConfigResp resp = new SocialConfigResp();
        resp.setSource(source);
        resp.setEnabled(SocialConfigKeys.parseStoredEnabled(
            value(configs, prefix + SocialConfigKeys.ENABLED_SUFFIX), source));
        resp.setClientId(value(configs, prefix + SocialConfigKeys.CLIENT_ID_SUFFIX));
        resp.setClientSecretConfigured(StringUtils.hasText(
            value(configs, prefix + SocialConfigKeys.CLIENT_SECRET_SUFFIX)));
        resp.setRedirectUri(value(configs, prefix + SocialConfigKeys.REDIRECT_URI_SUFFIX));
        resp.setPublicKey(SocialConfigKeys.ALIPAY_SOURCE.equals(source)
            ? value(configs, prefix + SocialConfigKeys.PUBLIC_KEY_SUFFIX) : "");
        return resp;
    }

    private void updateRequired(SysConfig config, String configValue) {
        if (config == null) {
            throw new BusinessException("系统参数不存在");
        }
        boolean updated = configService.update(new LambdaUpdateWrapper<SysConfig>()
            .eq(SysConfig::getId, config.getId())
            .eq(SysConfig::getConfigGroup, SocialConfigKeys.GROUP)
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
        if (SocialConfigKeys.ALIPAY_SOURCE.equals(source) && !StringUtils.hasText(publicKey)) {
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

    private static void ensureKeysExist(String source, Map<String, SysConfig> configs,
            Set<String> keys) {
        if (!configs.keySet().containsAll(keys)) {
            throw new BusinessException("第三方登录平台配置项不完整：" + source);
        }
    }

    private static String value(Map<String, SysConfig> configs, String key) {
        SysConfig config = configs.get(key);
        if (config == null) {
            throw new BusinessException("系统参数不存在：" + key);
        }
        return SocialConfigKeys.trimToEmpty(config.getConfigValue());
    }
}
