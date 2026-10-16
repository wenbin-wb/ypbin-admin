/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.service;

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabFeedback;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabFeedbackReq;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 牌账清-意见反馈服务接口。
 *
 * @author wenbin
 * @since 2026-10-16
 */
public interface CardtabFeedbackService extends BaseService<CardtabFeedback> {

    /**
     * 提交反馈。
     */
    void submit(CardtabFeedbackReq req);
}
