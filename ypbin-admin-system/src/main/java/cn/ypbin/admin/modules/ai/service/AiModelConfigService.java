/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.service;

import cn.ypbin.admin.modules.ai.entity.AiModelConfig;
import cn.ypbin.admin.modules.ai.model.req.AiModelConfigSaveReq;
import cn.ypbin.admin.modules.ai.model.resp.AiModelConfigResp;
import java.util.List;

/**
 * AI 模型配置业务接口。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiModelConfigService {

    /** 所有模型配置（API Key 脱敏） */
    List<AiModelConfigResp> listModels();

    /**
     * 按模型类型查询配置（API Key 脱敏）。
     *
     * @param modelType 模型类型，{@code null} 时返回全部
     */
    List<AiModelConfigResp> listModels(String modelType);

    /** 新增模型配置 */
    void createModel(AiModelConfigSaveReq req);

    /** 修改模型配置 */
    void updateModel(Long id, AiModelConfigSaveReq req);

    /** 删除模型配置 */
    void deleteModel(Long id);

    /** 设为默认模型（同一租户下其他模型 is_default 改为 0） */
    void setDefault(Long id);

    /** 启用/停用模型（默认模型禁止停用） */
    void updateStatus(Long id, Integer status);

    /** 复制模型配置（新配置默认非默认、启用，名称追加“（副本）”） */
    Long duplicate(Long id);

    /** 测试连通性（发一条 ping 消息，返回耗时 ms） */
    long testConnection(Long id);

    /** 获取当前租户的默认模型（启动 ChatModel 时使用，默认 CHAT 类型） */
    AiModelConfig getDefaultModel();

    /**
     * 按类型获取当前租户的默认模型。
     *
     * @param modelType 模型类型，{@code null} 默认 CHAT
     */
    AiModelConfig getDefaultModel(String modelType);
}
