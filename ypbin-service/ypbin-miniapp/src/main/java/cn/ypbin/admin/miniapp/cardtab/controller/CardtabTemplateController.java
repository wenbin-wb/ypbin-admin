/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.controller;

import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabFeedbackReq;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabGameTemplateSaveReq;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabGameTemplateResp;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabFeedbackService;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabGameTemplateService;
import cn.ypbin.starter.core.model.R;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 牌账清-玩法模板与意见反馈控制器。
 *
 * <p>两者都是「当前用户维度」的轻量功能：模板归属本人（列表只回自己的、删除校验归属），
 * 反馈仅提交（处理进度在后台侧推进），故不涉及房间级归属校验。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@RestController
@RequestMapping("/cardtab")
@RequiredArgsConstructor
public class CardtabTemplateController {

    private final CardtabGameTemplateService gameTemplateService;
    private final CardtabFeedbackService feedbackService;

    /**
     * 我的玩法模板列表。
     */
    @GetMapping("/game-templates")
    public R<List<CardtabGameTemplateResp>> listGameTemplates() {
        return R.ok(gameTemplateService.listMyTemplates());
    }

    /**
     * 保存玩法模板。
     */
    @PostMapping("/game-templates")
    public R<CardtabGameTemplateResp> saveGameTemplate(
        @Valid @RequestBody CardtabGameTemplateSaveReq req) {
        return R.ok(gameTemplateService.saveTemplate(req));
    }

    /**
     * 删除玩法模板（仅本人）。
     */
    @DeleteMapping("/game-templates/{templateId}")
    public R<Void> removeGameTemplate(@PathVariable Long templateId) {
        gameTemplateService.deleteTemplate(templateId);
        return R.ok();
    }

    /**
     * 提交意见反馈。
     */
    @PostMapping("/feedback")
    public R<Void> submitFeedback(@Valid @RequestBody CardtabFeedbackReq req) {
        feedbackService.submit(req);
        return R.ok();
    }
}
