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

import cn.ypbin.admin.modules.ai.entity.AiPromptTemplate;
import cn.ypbin.admin.modules.ai.model.req.AiPromptTemplateSaveReq;
import java.util.List;

/**
 * Prompt 模板服务。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiPromptTemplateService {

    /**
     * 查询当前租户启用中的模板列表。
     *
     * @return 按创建时间倒序的模板列表
     */
    List<AiPromptTemplate> listTemplates();

    /**
     * 新增模板（归属当前租户）。
     *
     * @param req 模板信息
     */
    void createTemplate(AiPromptTemplateSaveReq req);

    /**
     * 修改模板（仅允许改本租户模板）。
     *
     * @param id  模板 ID
     * @param req 模板信息
     */
    void updateTemplate(Long id, AiPromptTemplateSaveReq req);

    /**
     * 删除模板（仅允许删本租户模板）。
     *
     * @param id 模板 ID
     */
    void deleteTemplate(Long id);

    /**
     * 启用/停用模板。
     *
     * @param id     模板 ID
     * @param status 状态：1 正常 0 停用
     */
    void updateStatus(Long id, Integer status);
}
