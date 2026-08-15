/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.ai.entity.AiModelConfig;
import cn.ypbin.admin.system.ai.mapper.AiModelConfigMapper;
import cn.ypbin.admin.system.ai.model.req.AiModelConfigSaveReq;
import cn.ypbin.admin.system.ai.model.resp.AiModelConfigResp;
import cn.ypbin.admin.system.ai.service.AiModelConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 模型配置业务实现。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiModelConfigServiceImpl implements AiModelConfigService {

    private final AiModelConfigMapper modelConfigMapper;
    /** Spring AI 的 ChatModel（optional，用于连通性测试）*/
    private final ObjectProvider<ChatModel> chatModelProvider;

    @Override
    public List<AiModelConfigResp> listModels() {
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        List<AiModelConfig> list = modelConfigMapper.selectList(
            new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                .orderByDesc(AiModelConfig::getIsDefault)
                .orderByDesc(AiModelConfig::getCreateTime));
        return list.stream().map(this::toResp).toList();
    }

    @Override
    public void createModel(AiModelConfigSaveReq req) {
        AiModelConfig config = new AiModelConfig();
        BeanUtils.copyProperties(req, config);
        config.setTenantId(TenantContext.getTenantId().map(Long::intValue).orElse(1));
        config.setIsDefault(0);
        modelConfigMapper.insert(config);
    }

    @Override
    public void updateModel(Long id, AiModelConfigSaveReq req) {
        AiModelConfig existing = requireModel(id);
        BeanUtils.copyProperties(req, existing, "id", "tenantId", "isDefault");
        modelConfigMapper.updateById(existing);
    }

    @Override
    public void deleteModel(Long id) {
        AiModelConfig existing = requireModel(id);
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new BusinessException("默认模型不能删除，请先更换默认模型");
        }
        modelConfigMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        requireModel(id);
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        // 先清空同租户所有默认标记
        modelConfigMapper.update(null,
            new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                .set(AiModelConfig::getIsDefault, 0));
        // 设置新默认
        modelConfigMapper.update(null,
            new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getIsDefault, 1));
    }

    @Override
    public long testConnection(Long id) {
        requireModel(id);
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            throw new BusinessException("ChatModel 未配置，请先在 application.yml 配置模型 starter");
        }
        long start = System.currentTimeMillis();
        ChatClient.create(model).prompt().user("ping").call().content();
        return System.currentTimeMillis() - start;
    }

    @Override
    public AiModelConfig getDefaultModel() {
        Integer tenantId = TenantContext.getTenantId().map(Long::intValue).orElse(1);
        return modelConfigMapper.selectOne(
            new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                .eq(AiModelConfig::getIsDefault, 1)
                .eq(AiModelConfig::getStatus, 1)
                .last("LIMIT 1"));
    }

    private AiModelConfig requireModel(Long id) {
        AiModelConfig config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException("模型配置不存在");
        }
        return config;
    }

    private AiModelConfigResp toResp(AiModelConfig config) {
        AiModelConfigResp resp = new AiModelConfigResp();
        BeanUtils.copyProperties(config, resp, "apiKey");
        // API Key 脱敏
        if (config.getApiKey() != null && config.getApiKey().length() > 6) {
            resp.setApiKeyMasked(config.getApiKey().substring(0, 6) + "****");
        } else if (config.getApiKey() != null) {
            resp.setApiKeyMasked("****");
        }
        return resp;
    }
}
