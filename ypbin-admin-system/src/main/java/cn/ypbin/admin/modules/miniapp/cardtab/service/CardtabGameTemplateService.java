/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.service;

import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabGameTemplate;
import cn.ypbin.admin.modules.miniapp.cardtab.model.req.CardtabGameTemplateSaveReq;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabGameTemplateResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 牌账清-玩法模板服务接口。
 *
 * @author wenbin
 * @since 2026-10-16
 */
public interface CardtabGameTemplateService extends BaseService<CardtabGameTemplate> {

    /**
     * 我的模板列表。
     */
    List<CardtabGameTemplateResp> listMyTemplates();

    /**
     * 保存模板（新增）。
     */
    CardtabGameTemplateResp saveTemplate(CardtabGameTemplateSaveReq req);

    /**
     * 删除模板（仅本人）。
     */
    void deleteTemplate(Long id);
}
