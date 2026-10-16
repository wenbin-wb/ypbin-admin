/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.service.impl;

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabFeedback;
import cn.ypbin.admin.miniapp.cardtab.enums.CardtabFeedbackStatusEnum;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabFeedbackMapper;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabFeedbackReq;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabFeedbackService;
import cn.ypbin.admin.miniapp.cardtab.support.CardtabRoomAccessSupport;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 牌账清-意见反馈服务实现。
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Service
@RequiredArgsConstructor
public class CardtabFeedbackServiceImpl extends BaseServiceImpl<CardtabFeedbackMapper, CardtabFeedback>
    implements CardtabFeedbackService {

    private final CardtabRoomAccessSupport accessSupport;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(CardtabFeedbackReq req) {
        CardtabFeedback feedback = new CardtabFeedback();
        feedback.setUserId(accessSupport.currentUserId());
        feedback.setContent(req.getContent().trim());
        feedback.setContact(StringUtils.hasText(req.getContact()) ? req.getContact().trim() : null);
        feedback.setFeedbackStatus(CardtabFeedbackStatusEnum.PENDING.getCode());
        save(feedback);
    }
}
