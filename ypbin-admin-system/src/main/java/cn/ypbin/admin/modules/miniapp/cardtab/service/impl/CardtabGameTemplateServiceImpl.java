/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.service.impl;

import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabGameTemplate;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabGameTemplateMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.model.req.CardtabGameTemplateSaveReq;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabGameTemplateResp;
import cn.ypbin.admin.modules.miniapp.cardtab.service.CardtabGameTemplateService;
import cn.ypbin.admin.modules.miniapp.cardtab.support.CardtabRoomAccessSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 牌账清-玩法模板服务实现。
 *
 * <p><b>归属</b>：模板归属创建者本人（{@code owner_user_id}），所有读写都以当前用户维度收口——
 * 列表只查自己的、删除前校验归属，避免按 {@code id} 遍历删改他人模板。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Service
@RequiredArgsConstructor
public class CardtabGameTemplateServiceImpl
    extends BaseServiceImpl<CardtabGameTemplateMapper, CardtabGameTemplate>
    implements CardtabGameTemplateService {

    private final CardtabRoomAccessSupport accessSupport;

    @Override
    public List<CardtabGameTemplateResp> listMyTemplates() {
        Long userId = accessSupport.currentUserId();
        List<CardtabGameTemplate> list = list(new LambdaQueryWrapper<CardtabGameTemplate>()
            .eq(CardtabGameTemplate::getOwnerUserId, userId)
            .orderByDesc(CardtabGameTemplate::getId));
        if (CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        return list.stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardtabGameTemplateResp saveTemplate(CardtabGameTemplateSaveReq req) {
        Long userId = accessSupport.currentUserId();

        CardtabGameTemplate template = new CardtabGameTemplate();
        template.setOwnerUserId(userId);
        template.setName(req.getName().trim());
        template.setGameType(req.getGameType().trim());
        template.setUnit(req.getUnit().trim());
        template.setBookkeepingMode(StringUtils.hasText(req.getBookkeepingMode())
            ? req.getBookkeepingMode().trim() : null);
        template.setAllowPasscodeJoin(Boolean.TRUE.equals(req.getAllowPasscodeJoin()) ? 1 : 0);
        save(template);

        return toResp(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        Long userId = accessSupport.currentUserId();
        CardtabGameTemplate template = getById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        // 不区分「不存在」与「非本人」：避免借报错差异探测他人模板是否存在
        if (!userId.equals(template.getOwnerUserId())) {
            throw new BusinessException("模板不存在");
        }
        removeById(id);
    }

    private CardtabGameTemplateResp toResp(CardtabGameTemplate t) {
        CardtabGameTemplateResp resp = new CardtabGameTemplateResp();
        resp.setId(t.getId());
        resp.setName(t.getName());
        resp.setGameType(t.getGameType());
        resp.setUnit(t.getUnit());
        resp.setBookkeepingMode(t.getBookkeepingMode());
        resp.setAllowPasscodeJoin(t.getAllowPasscodeJoin() != null && t.getAllowPasscodeJoin() == 1);
        return resp;
    }
}
