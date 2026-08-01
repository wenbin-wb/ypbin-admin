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
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.model.query.ConfigQuery;
import cn.ypbin.admin.system.model.req.ConfigSaveReq;
import cn.ypbin.admin.system.model.req.ConfigUpdateBatchReq;
import cn.ypbin.admin.system.model.resp.ConfigResp;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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

    /** configKey -> configValue 本地缓存 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

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
            return defaultValue;
        }
    }

    @Override
    public PageResult<ConfigResp> pageConfigs(ConfigQuery query) {
        PageResult<SysConfig> source = page(query, new LambdaQueryWrapper<SysConfig>()
            .eq(StringUtils.hasText(query.getConfigGroup()), SysConfig::getConfigGroup, query.getConfigGroup())
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
    public void createConfig(ConfigSaveReq req) {
        checkKeyUnique(req.getConfigKey(), null);
        SysConfig config = new SysConfig();
        BeanUtils.copyProperties(req, config);
        config.setBuiltIn(0);
        save(config);
        refreshCache();
    }

    @Override
    public void updateConfig(Long id, ConfigSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("参数不存在");
        }
        checkKeyUnique(req.getConfigKey(), id);
        SysConfig config = new SysConfig();
        BeanUtils.copyProperties(req, config);
        config.setId(id);
        updateById(config);
        refreshCache();
    }

    @Override
    public void deleteConfig(Long id) {
        SysConfig config = getById(id);
        if (config == null) {
            throw new BusinessException("参数不存在");
        }
        if (config.getBuiltIn() != null && config.getBuiltIn() == 1) {
            throw new BusinessException("内置参数不可删除");
        }
        removeById(id);
        refreshCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBatch(ConfigUpdateBatchReq req) {
        if (req.getConfigs() == null || req.getConfigs().isEmpty()) {
            return;
        }
        req.getConfigs().forEach((key, value) -> update(new LambdaUpdateWrapper<SysConfig>()
            .eq(SysConfig::getConfigKey, key)
            .set(SysConfig::getConfigValue, value)));
        refreshCache();
    }

    @Override
    public void refreshCache() {
        Map<String, String> fresh = new ConcurrentHashMap<>();
        for (SysConfig config : list()) {
            if (config.getConfigKey() != null && config.getConfigValue() != null) {
                fresh.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        cache.clear();
        cache.putAll(fresh);
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigKey, configKey)
            .ne(excludeId != null, SysConfig::getId, excludeId));
        if (exists) {
            throw new BusinessException("参数键已存在：" + configKey);
        }
    }

    private ConfigResp toResp(SysConfig config) {
        ConfigResp resp = new ConfigResp();
        BeanUtils.copyProperties(config, resp);
        return resp;
    }
}
