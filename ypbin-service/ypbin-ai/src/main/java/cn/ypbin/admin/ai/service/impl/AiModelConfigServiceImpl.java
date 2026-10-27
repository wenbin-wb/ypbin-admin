/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service.impl;

import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.admin.ai.core.AiKeyCipher;
import cn.ypbin.admin.ai.entity.AiModelConfig;
import cn.ypbin.admin.ai.mapper.AiModelConfigMapper;
import cn.ypbin.admin.ai.model.req.AiModelConfigSaveReq;
import cn.ypbin.admin.ai.model.resp.AiModelConfigResp;
import cn.ypbin.admin.ai.service.AiModelConfigService;
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

    /** 连接超时（秒） */
    static final int CONNECT_TIMEOUT_SECONDS = 5;
    /** 请求超时（秒） */
    static final int READ_TIMEOUT_SECONDS = 15;
    /** 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS);
    /** 请求超时 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(READ_TIMEOUT_SECONDS);

    /**
     * 连接测试复用同一客户端：每次 new 会丢掉连接池与线程资源。
     *
     * <p>显式指定 HTTP/1.1：JDK 默认 HTTP/2，而该方法总是「新连接 + 带体 POST」，
     * 对仅支持 h2c 探测的服务会出现 {@code EOF reached while reading}（IoT 仓实测）。</p>
     */
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(CONNECT_TIMEOUT)
        .build();

    /**
     * 取共享客户端（包级可见，仅供测试断言「单例 + 协议版本 + 超时」配置，不做真实网络调用）。
     *
     * @return 进程内共享的 {@link HttpClient}
     */
    static HttpClient httpClient() {
        return httpClient;
    }

    private final AiModelConfigMapper modelConfigMapper;
    private final AiKeyCipher keyCipher;

    @Override
    public List<AiModelConfigResp> listModels() {
        return listModels(null);
    }

    @Override
    public List<AiModelConfigResp> listModels(String modelType) {
        return listModels(modelType, null);
    }

    @Override
    public List<AiModelConfigResp> listModels(String modelType, Integer status) {
        Long tenantId = currentTenantId();
        // 状态：null=仅启用（与历史行为一致），0/1 精确过滤供管理端找回已停用模型
        Integer filterStatus = status == null ? EntityStatus.ENABLED.getCode() : status;
        LambdaQueryWrapper<AiModelConfig> wrapper = new LambdaQueryWrapper<AiModelConfig>()
            .eq(AiModelConfig::getTenantId, tenantId)
            .eq(AiModelConfig::getStatus, filterStatus);
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
        if (modelConfigMapper.insert(config) != 1) {
            throw new BusinessException("模型配置创建失败");
        }
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
        if (modelConfigMapper.updateById(config) != 1) {
            throw new BusinessException("模型配置更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModel(Long id) {
        AiModelConfig existing = requireModel(id);
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new BusinessException("默认模型不能删除，请先更换默认模型");
        }
        if (modelConfigMapper.deleteById(id) != 1) {
            throw new BusinessException("模型配置删除失败");
        }
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
        int affected = modelConfigMapper.update(null,
            new LambdaUpdateWrapper<AiModelConfig>()
                .eq(AiModelConfig::getId, id)
                .set(AiModelConfig::getIsDefault, 1));
        if (affected != 1) {
            throw new BusinessException("默认模型设置失败");
        }
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
        if (modelConfigMapper.updateById(update) != 1) {
            throw new BusinessException("模型状态更新失败");
        }
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
        copy.setStatus(EntityStatus.ENABLED.getCode());
        // API Key 密文可直接复用（同一把密钥加密，解密语义不变）
        if (modelConfigMapper.insert(copy) != 1) {
            throw new BusinessException("模型复制失败");
        }
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
            // 模型名来自用户输入，直接拼进 JSON 会被引号/反斜杠破坏报文（甚至注入字段）
            String payload = """
                {"model":"%s","messages":[{"role":"user","content":"ping"}],"max_tokens":5}
                """.formatted(escapeJson(config.getModelName()));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            String[] candidates = completionUrls(baseUrl);
            HttpResponse<String> resp = null;
            for (String url : candidates) {
                resp = httpClient.send(builder.copy().uri(URI.create(url)).build(),
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

    /**
     * JSON 字符串转义（模型名/地址只用于报文，不引入额外依赖）。
     *
     * <p>除 {@code "} 与 {@code \} 外，还必须转义 U+0000–U+001F 控制字符：
     * JSON 规范禁止裸写控制字符，模型名里混入换行/制表符会让整条报文非法，
     * 服务端直接返回 400，而错误信息里只显示「连接失败」，排查不到根因。</p>
     *
     * @param value 原始字符串，可空
     * @return 可直接放入 JSON 双引号内的字符串
     */
    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
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
                .eq(AiModelConfig::getStatus, EntityStatus.ENABLED.getCode())
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
