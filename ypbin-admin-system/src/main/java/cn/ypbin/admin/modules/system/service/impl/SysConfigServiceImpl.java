/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import cn.ypbin.admin.modules.system.entity.SysConfig;
import cn.ypbin.admin.modules.system.mapper.SysConfigMapper;
import cn.ypbin.admin.modules.system.model.query.ConfigQuery;
import cn.ypbin.admin.modules.system.model.req.ConfigSaveReq;
import cn.ypbin.admin.modules.system.model.req.ConfigUpdateBatchReq;
import cn.ypbin.admin.modules.system.model.resp.ConfigResp;
import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.admin.modules.system.social.ConfigChangedEvent;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统参数服务实现。参数读多写少，用本地 Map 缓存，写操作后整体刷新。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl extends BaseServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    /** 布尔真值集合 */
    private static final Set<String> TRUE_VALUES = Set.of("true", "1", "on", "yes");

    /** 不允许通过查询接口返回的敏感参数 */
    private static final Set<String> SENSITIVE_CONFIG_KEYS = Set.of(
        "SMS_ACCESS_KEY_SECRET",
        "MAIL_PASSWORD");

    /** configKey -> configValue 本地不可变快照 */
    private volatile Map<String, String> cache = Map.of();

    private final ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        refreshCache();
    }

    @Override
    public String getString(String key, String defaultValue) {
        String value = cache.get(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = cache.get(key);
        return value == null ? defaultValue : TRUE_VALUES.contains(value.trim().toLowerCase());
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("系统参数必须为整数：" + key);
        }
    }

    @Override
    public PageResult<ConfigResp> pageConfigs(ConfigQuery query) {
        PageResult<SysConfig> source = page(query, new LambdaQueryWrapper<SysConfig>()
            .eq(StringUtils.hasText(query.getConfigGroup()), SysConfig::getConfigGroup, query.getConfigGroup())
            .eq(query.getBuiltIn() != null, SysConfig::getBuiltIn, query.getBuiltIn())
            .like(StringUtils.hasText(query.getName()), SysConfig::getName, query.getName())
            .like(StringUtils.hasText(query.getConfigKey()), SysConfig::getConfigKey, query.getConfigKey())
            .orderByAsc(SysConfig::getConfigGroup));
        List<ConfigResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    public List<ConfigResp> listByGroup(String configGroup) {
        return list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, configGroup))
            .stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createConfig(ConfigSaveReq req) {
        rejectSocialConfig(req.getConfigGroup(), req.getConfigKey());
        checkKeyUnique(req.getConfigKey(), null);
        SysConfig config = new SysConfig();
        BeanUtils.copyProperties(req, config);
        config.setBuiltIn(0);
        if (!save(config)) {
            throw new BusinessException("参数新增失败");
        }
        publishConfigChanged(req.getConfigGroup());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(Long id, ConfigSaveReq req) {
        SysConfig existing = getById(id);
        if (existing == null) {
            throw new BusinessException("参数不存在");
        }
        rejectSocialConfig(existing);
        rejectSocialConfig(req.getConfigGroup(), req.getConfigKey());
        if (Integer.valueOf(1).equals(existing.getBuiltIn())
            && (!existing.getConfigKey().equals(req.getConfigKey())
            || !existing.getConfigGroup().equals(req.getConfigGroup())
            || !existing.getName().equals(req.getName()))) {
            throw new BusinessException("内置参数不可修改参数键、分组或名称");
        }
        checkKeyUnique(req.getConfigKey(), id);
        SysConfig config = new SysConfig();
        BeanUtils.copyProperties(req, config);
        config.setId(id);
        if (!updateById(config)) {
            throw new BusinessException("参数更新失败");
        }
        publishConfigChanged(existing.getConfigGroup(), req.getConfigGroup());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        SysConfig config = getById(id);
        if (config == null) {
            throw new BusinessException("参数不存在");
        }
        rejectSocialConfig(config);
        if (Integer.valueOf(1).equals(config.getBuiltIn())) {
            throw new BusinessException("内置参数不可删除");
        }
        if (!removeById(id)) {
            throw new BusinessException("参数删除失败");
        }
        publishConfigChanged(config.getConfigGroup());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGroup(String configGroup, ConfigUpdateBatchReq req) {
        if (!StringUtils.hasText(configGroup)) {
            throw new BusinessException("参数分组不能为空");
        }
        rejectSocialGroup(configGroup);
        Set<String> keys = req.getConfigs().keySet();
        if (keys.stream().anyMatch(key -> !StringUtils.hasText(key))) {
            throw new BusinessException("参数键不能为空");
        }
        if (req.getConfigs().values().stream().anyMatch(value -> value == null)) {
            throw new BusinessException("参数值不能为空");
        }
        if (keys.stream().anyMatch(key -> key.startsWith("SOCIAL_"))) {
            throw new BusinessException("第三方登录配置必须使用专用接口维护");
        }
        List<SysConfig> configs = list(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigGroup, configGroup)
            .in(SysConfig::getConfigKey, keys));
        if (configs.size() != keys.size()) {
            throw new BusinessException("包含未知参数或跨分组参数");
        }
        for (SysConfig config : configs) {
            config.setConfigValue(req.getConfigs().get(config.getConfigKey()));
            if (!updateById(config)) {
                throw new BusinessException("参数更新失败：" + config.getConfigKey());
            }
        }
        publishConfigChanged(configGroup);
    }

    @Override
    public void refreshCache() {
        Map<String, String> fresh = new HashMap<>();
        for (SysConfig config : list()) {
            if (config.getConfigKey() != null && config.getConfigValue() != null) {
                fresh.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        cache = Map.copyOf(fresh);
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigKey, configKey)
            .ne(excludeId != null, SysConfig::getId, excludeId));
        if (exists) {
            throw new BusinessException("参数键已存在：" + configKey);
        }
    }

    private void publishConfigChanged(String... configGroups) {
        boolean smsChanged = false;
        for (String configGroup : configGroups) {
            if ("sms".equals(configGroup)) {
                smsChanged = true;
                break;
            }
        }
        eventPublisher.publishEvent(new ConfigChangedEvent(smsChanged));
    }

    private static void rejectSocialGroup(String configGroup) {
        if ("social".equalsIgnoreCase(configGroup)) {
            throw new BusinessException("第三方登录配置必须使用专用接口维护");
        }
    }

    private static void rejectSocialConfig(String configGroup, String configKey) {
        rejectSocialGroup(configGroup);
        if (configKey != null && configKey.startsWith("SOCIAL_")) {
            throw new BusinessException("第三方登录配置必须使用专用接口维护");
        }
    }

    private static void rejectSocialConfig(SysConfig config) {
        rejectSocialConfig(config.getConfigGroup(), config.getConfigKey());
    }

    private ConfigResp toResp(SysConfig config) {
        ConfigResp resp = new ConfigResp();
        BeanUtils.copyProperties(config, resp);
        String configKey = config.getConfigKey();
        if (configKey != null
            && (SENSITIVE_CONFIG_KEYS.contains(configKey)
            || configKey.startsWith("SOCIAL_") && configKey.endsWith("_CLIENT_SECRET"))) {
            resp.setConfigValue("");
        }
        return resp;
    }
}
