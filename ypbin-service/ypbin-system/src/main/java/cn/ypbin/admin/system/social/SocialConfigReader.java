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
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 第三方登录平台配置读取（内部 Feign 契约侧）。
 *
 * <p>从系统参数（sys_config 的 SOCIAL_* 键）组装含密钥明文的 {@link SocialAuthConfig}，
 * 供 auth-svc 经内部端点拉取后构建 JustAuth 授权请求。密钥明文仅限内部传递。</p>
 *
 * <p>配置键与解析规则由 {@link SocialConfigKeys} 提供（与管理面同一份），
 * 本类只负责「组装」。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
@Component
@RequiredArgsConstructor
public class SocialConfigReader {

    private final SysConfigService configService;

    /**
     * 读取指定平台的授权配置（含密钥明文）。
     *
     * @param source 平台标识
     * @return 授权配置
     */
    public SocialAuthConfig read(String source) {
        String normalizedSource = SocialConfigKeys.normalizeSource(source);
        List<SysConfig> configs = loadPlatformConfigs(normalizedSource);
        String prefix = SocialConfigKeys.keyPrefix(normalizedSource);
        SocialAuthConfig config = new SocialAuthConfig();
        config.setSource(normalizedSource);
        config.setEnabled(SocialConfigKeys.parseStoredEnabled(
            value(configs, prefix + SocialConfigKeys.ENABLED_SUFFIX), normalizedSource));
        config.setClientId(value(configs, prefix + SocialConfigKeys.CLIENT_ID_SUFFIX));
        config.setClientSecret(value(configs, prefix + SocialConfigKeys.CLIENT_SECRET_SUFFIX));
        config.setRedirectUri(value(configs, prefix + SocialConfigKeys.REDIRECT_URI_SUFFIX));
        config.setPublicKey(SocialConfigKeys.ALIPAY_SOURCE.equals(normalizedSource)
            ? value(configs, prefix + SocialConfigKeys.PUBLIC_KEY_SUFFIX) : "");
        return config;
    }

    /**
     * 读取全部已启用平台的授权配置。
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
            .eq(SysConfig::getConfigGroup, SocialConfigKeys.GROUP));
        List<String> sources = new ArrayList<>();
        for (SysConfig config : configs) {
            if (config.getConfigKey() == null
                || !config.getConfigKey().endsWith(SocialConfigKeys.ENABLED_SUFFIX)) {
                continue;
            }
            String source = SocialConfigKeys.sourceOf(config.getConfigKey());
            if (source.isEmpty()) {
                continue;
            }
            // 与 read() 共用同一解析口：选中后必定读得出来（历史值 1/0 也兼容）
            if (SocialConfigKeys.parseStoredEnabled(config.getConfigValue(), source)) {
                sources.add(source);
            }
        }
        return sources;
    }

    private List<SysConfig> loadPlatformConfigs(String source) {
        String prefix = SocialConfigKeys.keyPrefix(source);
        List<SysConfig> configs = configService.list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, SocialConfigKeys.GROUP)
            .likeRight(SysConfig::getConfigKey, prefix));
        if (configs.isEmpty()) {
            throw new BusinessException("第三方登录平台配置不存在：" + source);
        }
        return configs;
    }

    private static String value(List<SysConfig> configs, String key) {
        for (SysConfig config : configs) {
            if (key.equals(config.getConfigKey())) {
                return SocialConfigKeys.trimToEmpty(config.getConfigValue());
            }
        }
        return "";
    }
}
