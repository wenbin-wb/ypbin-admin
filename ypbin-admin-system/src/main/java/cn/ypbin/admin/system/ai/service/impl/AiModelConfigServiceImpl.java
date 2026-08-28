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

import cn.ypbin.admin.system.ai.core.AiKeyCipher;
import cn.ypbin.admin.system.ai.entity.AiModelConfig;
import cn.ypbin.admin.system.ai.mapper.AiModelConfigMapper;
import cn.ypbin.admin.system.ai.model.req.AiModelConfigSaveReq;
import cn.ypbin.admin.system.ai.model.resp.AiModelConfigResp;
import cn.ypbin.admin.system.ai.service.AiModelConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
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

    /** 对话模型 */
    public static final String MODEL_TYPE_CHAT = "CHAT";
    /** 向量化模型 */
    public static final String MODEL_TYPE_EMBEDDING = "EMBEDDING";

    private final AiModelConfigMapper modelConfigMapper;
    private final AiKeyCipher keyCipher;

    @Override
    public List<AiModelConfigResp> listModels() {
        return listModels(null);
    }

    @Override
    public List<AiModelConfigResp> listModels(String modelType) {
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<AiModelConfig>()
            .eq(AiModelConfig::getTenantId, tenantId)
            .eq(AiModelConfig::getStatus, 1);
        if (modelType != null && !modelType.isBlank()) {
            wrapper.eq(AiModelConfig::getModelType, modelType);
        }
        wrapper.orderByAsc(AiModelConfig::getModelType)
            .orderByDesc(AiModelConfig::getIsDefault)
            .orderByDesc(AiModelConfig::getCreateTime);
        List<AiModelConfig> list = modelConfigMapper.selectList(wrapper);
        return list.stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createModel(AiModelConfigSaveReq req) {
        AiModelConfig config = new AiModelConfig();
        BeanUtils.copyProperties(req, config);
        config.setTenantId(currentTenantId());
        // 类型缺省按对话模型处理，兼容存量调用
        if (config.getModelType() == null || config.getModelType().isBlank()) {
            config.setModelType(MODEL_TYPE_CHAT);
        }
        config.setIsDefault(0);
        // API Key AES-GCM 加密后存储，禁止明文落库
        config.setApiKey(keyCipher.encrypt(req.getApiKey()));
        modelConfigMapper.insert(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(Long id, AiModelConfigSaveReq req) {
        requireModel(id);
        AiModelConfig config = new AiModelConfig();
        BeanUtils.copyProperties(req, config, "id", "tenantId", "isDefault", "status", "apiKey");
        config.setId(id);
        // 留空表示不修改，非空则重新加密
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            config.setApiKey(keyCipher.encrypt(req.getApiKey()));
        }
        modelConfigMapper.updateById(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        AiModelConfig target = requireModel(id);
        Long tenantId = currentTenantId();
        String modelType = target.getModelType() == null ? MODEL_TYPE_CHAT : target.getModelType();
        // 先清空同租户同类型的所有默认标记，避免不同类型互相覆盖
        modelConfigMapper.update(null,
            new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                .eq(AiModelConfig::getModelType, modelType)
                .set(AiModelConfig::getIsDefault, 0));
        // 设置新默认
        modelConfigMapper.update(null,
            new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getIsDefault, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        AiModelConfig config = requireModel(id);
        if (status != null && status == 0
            && config.getIsDefault() != null && config.getIsDefault() == 1) {
            throw new BusinessException("默认模型不能停用，请先更换默认模型");
        }
        AiModelConfig update = new AiModelConfig();
        update.setId(id);
        update.setStatus(status);
        modelConfigMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long duplicate(Long id) {
        AiModelConfig source = requireModel(id);
        AiModelConfig copy = new AiModelConfig();
        BeanUtils.copyProperties(source, copy, "id", "isDefault", "status",
            "createTime", "updateTime", "createUser", "updateUser");
        copy.setName(source.getName() + "（副本）");
        copy.setIsDefault(0);
        copy.setStatus(1);
        // API Key 密文可直接复用（同一把密钥加密，解密语义不变）
        modelConfigMapper.insert(copy);
        return copy.getId();
    }

    @Override
    public long testConnection(Long id) {
        AiModelConfig config = requireModel(id);
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException("该模型未配置接口地址（baseUrl），无法测试");
        }
        String apiKey = keyCipher.decrypt(config.getApiKey());
        long start = System.currentTimeMillis();
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            String payload = """
                {"model":"%s","messages":[{"role":"user","content":"ping"}],"max_tokens":5}
                """.formatted(config.getModelName());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            String[] candidates = completionUrls(baseUrl);
            HttpResponse<String> resp = null;
            for (String url : candidates) {
                resp = client.send(builder.copy().uri(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString());
                // 404 时尝试下一个候选地址，其余错误码直接返回
                if (resp.statusCode() != 404) {
                    break;
                }
            }
            if (resp == null || resp.statusCode() != 200) {
                int code = resp == null ? 0 : resp.statusCode();
                String body = resp == null ? "" : resp.body();
                throw new BusinessException("连接失败（HTTP " + code + "）："
                    + (body == null || body.isBlank() ? "无响应内容" : truncate(body)));
            }
            return System.currentTimeMillis() - start;
        } catch (IOException e) {
            throw new BusinessException("连接失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("连接超时或中断");
        }
    }

    /**
     * 根据用户填写的 baseUrl 推导 OpenAI 兼容的 chat/completions 地址：
     * 依次尝试原路径、补 /v1 前缀、去掉多余 /v1 前缀。
     */
    private String[] completionUrls(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return new String[] {normalized};
        }
        if (normalized.endsWith("/v1")) {
            return new String[] {normalized + "/chat/completions"};
        }
        // 根路径（如 https://api.deepseek.com）：先试 /chat/completions，404 再试 /v1/chat/completions
        return new String[] {normalized + "/chat/completions", normalized + "/v1/chat/completions"};
    }

    private String truncate(String text) {
        return text == null ? "" : (text.length() > 300 ? text.substring(0, 300) + "..." : text);
    }

    @Override
    public AiModelConfig getDefaultModel() {
        return getDefaultModel(MODEL_TYPE_CHAT);
    }

    @Override
    public AiModelConfig getDefaultModel(String modelType) {
        Long tenantId = currentTenantId();
        String type = modelType == null || modelType.isBlank() ? MODEL_TYPE_CHAT : modelType;
        return modelConfigMapper.selectOne(
            new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getTenantId, tenantId)
                .eq(AiModelConfig::getModelType, type)
                .eq(AiModelConfig::getIsDefault, 1)
                .eq(AiModelConfig::getStatus, 1)
                .last("LIMIT 1"));
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
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
