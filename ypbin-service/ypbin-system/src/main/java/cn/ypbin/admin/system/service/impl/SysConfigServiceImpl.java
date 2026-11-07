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
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.model.query.ConfigQuery;
import cn.ypbin.admin.system.model.req.ConfigSaveReq;
import cn.ypbin.admin.system.model.req.ConfigUpdateBatchReq;
import cn.ypbin.admin.system.model.resp.ConfigResp;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.service.support.ConfigValueMasker;
import cn.ypbin.admin.system.social.ConfigChangedEvent;
import cn.ypbin.starter.cache.annotation.CacheEvict;
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

    /** 新增参数时提交脱敏占位值的拒绝文案（新增没有「原值」可保留，只能显式拒绝） */
    private static final String MASKED_VALUE_REJECT_MESSAGE =
        "参数值不能是脱敏后的占位值（**** 开头），请输入真实值";

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
    @CacheEvict(keys = {"'sys:config:key:' + #req.configKey"})
    public void createConfig(ConfigSaveReq req) {
        rejectSocialConfig(req.getConfigGroup(), req.getConfigKey());
        checkKeyUnique(req.getConfigKey(), null);
        // 新增时提交的是**脱敏占位值** ⇒ 无原值可保留，只能显式拒绝（不能把 ****abcd 当真实密钥存库）。
        // 同样必须先判「键敏感」：非敏感键的真实值可以合法地以 4 个星号开头，不能误拒。
        if (ConfigValueMasker.isSensitive(req.getConfigKey())
            && ConfigValueMasker.isMaskedValue(req.getConfigValue())) {
            throw new BusinessException(MASKED_VALUE_REJECT_MESSAGE);
        }
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
    @CacheEvict(keys = {"'sys:config:key:' + #req.configKey"})
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
        // 列表页回显的是脱敏值；管理员改其它字段时原样提交回来 ⇒ 视为「未修改」，保留库内真实值，
        // 避免把 ****末4位 当成真实密钥写库（那会让密钥静默损坏且不报错）
        config.setConfigValue(resolveSubmittedValue(req.getConfigKey(), req.getConfigValue(),
            existing.getConfigValue()));
        if (!updateById(config)) {
            throw new BusinessException("参数更新失败");
        }
        // 键可被修改：旧键的 Redis 缓存须一并清除（新键由 @CacheEvict 清），防读侧取旧值
        SysCache.evictConfig(existing.getConfigKey());
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
        SysCache.evictConfig(config.getConfigKey());
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
            config.setConfigValue(resolveSubmittedValue(config.getConfigKey(),
                req.getConfigs().get(config.getConfigKey()), config.getConfigValue()));
        }
        // 分组更新是「同一批参数一起保存」：逐条 update 是 N 次往返且中途失败会留下半更新
        if (!updateBatchById(configs)) {
            throw new BusinessException("参数批量更新失败");
        }
        // 失效 Redis 缓存（auth 等跨服务读取经 SysCache），合并键后一次删除；
        // 防开关/密钥变更不生效，也避免按参数个数放大缓存往返
        SysCache.evictConfigs(keys);
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

    /**
     * 解析提交值：<b>目标键敏感</b>且提交的是脱敏占位值（{@code ****末4位}）时，保留库内原值（视为「未修改」）。
     *
     * <p><strong>为什么需要</strong>：列表页与分组表单回显的是脱敏值（见 {@link #toResp}），管理员只改
     * 其它字段时会把掩码原样提交回来。若直接写库，真实密钥会被 {@code ****abcd} 覆盖——既不报错、也无法
     * 从响应看出，是最难排查的一类静默损坏。故此处显式跳过，并保留原值。</p>
     *
     * <p><strong>为什么必须先判「键敏感」</strong>：掩码识别只看值的形状（4 个星号开头）。非敏感键的真实
     * 值完全可能以此开头（例如自定义项 {@code ****brand-new-name}），若不判键就会把它当成「未修改」，
     * 用户的真实修改被<b>静默丢弃</b>、无任何报错。因此本方法先问
     * {@link ConfigValueMasker#isSensitive(String)}：非敏感键<b>原样写库</b>。</p>
     *
     * @param configKey      目标配置键（用于判定该键是否敏感）
     * @param submittedValue 请求提交的值
     * @param existingValue  库内当前真实值
     * @return 应当写库的值
     */
    private static String resolveSubmittedValue(String configKey, String submittedValue,
            String existingValue) {
        if (!ConfigValueMasker.isSensitive(configKey)) {
            return submittedValue;
        }
        return ConfigValueMasker.isMaskedValue(submittedValue) ? existingValue : submittedValue;
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

    /**
     * 实体 → 列表页响应：敏感参数值按 {@link ConfigValueMasker} 统一脱敏。
     *
     * <p><strong>口径已与内部端点（{@code SystemClientImpl#getConfigByKey}）统一</strong>：
     * 同一份 {@code sys_config} 经列表页与经 internal 端点读到的是同一个形态（{@code ****末4位}），
     * 判据也同为「后缀集合」。此前列表页用 2 个精确键 + 置空 {@code ""}，而 internal 用 6 个后缀 + 掩码，
     * 两者判据不同 ⇒ <b>存在「internal 打码、列表页返回明文」的泄露面</b>（凡命中后缀但不属那 2 个精确键
     * 的配置项，管理面会把密钥原文回显）。现统一到 {@link ConfigValueMasker} 一处，该泄露面消除。</p>
     *
     * @param config 系统参数实体
     * @return 脱敏后的响应
     */
    private ConfigResp toResp(SysConfig config) {
        ConfigResp resp = new ConfigResp();
        BeanUtils.copyProperties(config, resp);
        resp.setConfigValue(ConfigValueMasker.mask(config.getConfigKey(), config.getConfigValue()));
        return resp;
    }
}
