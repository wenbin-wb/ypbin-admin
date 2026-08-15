/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service;

import cn.ypbin.admin.system.ai.entity.AiModelConfig;
import cn.ypbin.admin.system.ai.model.req.AiModelConfigSaveReq;
import cn.ypbin.admin.system.ai.model.resp.AiModelConfigResp;
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

    /** 新增模型配置 */
    void createModel(AiModelConfigSaveReq req);

    /** 修改模型配置 */
    void updateModel(Long id, AiModelConfigSaveReq req);

    /** 删除模型配置 */
    void deleteModel(Long id);

    /** 设为默认模型（同一租户下其他模型 is_default 改为 0） */
    void setDefault(Long id);

    /** 测试连通性（发一条 ping 消息，返回耗时 ms） */
    long testConnection(Long id);

    /** 获取当前租户的默认模型（启动 ChatModel 时使用） */
    AiModelConfig getDefaultModel();
}
