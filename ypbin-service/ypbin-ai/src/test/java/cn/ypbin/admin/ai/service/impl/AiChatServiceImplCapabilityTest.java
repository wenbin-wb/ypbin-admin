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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.ai.entity.AiModelConfig;
import cn.ypbin.admin.ai.mapper.AiChatMessageMapper;
import cn.ypbin.admin.ai.mapper.AiChatRoleMapper;
import cn.ypbin.admin.ai.mapper.AiChatSessionMapper;
import cn.ypbin.admin.ai.model.req.AiChatSendReq;
import cn.ypbin.admin.ai.model.req.AiChatSessionCreateReq;
import cn.ypbin.admin.ai.service.AiModelConfigService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * AI 对话入参能力校验：接收了但做不到的字段必须显式拒绝，不能静默忽略。
 *
 * <p>覆盖三类此前「传了不生效」的入参：{@code images}（多模态未接入）、
 * {@code stream=false}（接口固定流式）、{@code modelId}（starter 只按租户默认模型
 * 构建 ChatClient，无法按会话切换）。断言落在「抛业务异常且不触碰任何表」，
 * 防止后续修改把拒绝逻辑挪到落库之后。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
class AiChatServiceImplCapabilityTest {

    private AiChatSessionMapper sessionMapper;

    private AiModelConfigService modelConfigService;

    private AiChatServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionMapper = mock(AiChatSessionMapper.class);
        AiChatMessageMapper messageMapper = mock(AiChatMessageMapper.class);
        AiChatRoleMapper roleMapper = mock(AiChatRoleMapper.class);
        AiKnowledgeCrudComponent knowledgeCrudComponent = mock(AiKnowledgeCrudComponent.class);
        modelConfigService = mock(AiModelConfigService.class);
        ObjectProvider<AiChatService> provider = mock(ObjectProvider.class);
        service = new AiChatServiceImpl(sessionMapper, messageMapper, roleMapper,
            knowledgeCrudComponent, modelConfigService, provider,
            mock(PlatformTransactionManager.class));
    }

    @Test
    @DisplayName("★ 图片输入：多模态未接入时明确拒绝，且不落任何库")
    void shouldRejectImages() {
        AiChatSendReq req = new AiChatSendReq();
        req.setContent("这张图里是什么");
        req.setImages(List.of("https://example.com/a.png"));

        assertThatThrownBy(() -> service.sendMessageStream(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持图片输入");
        verifyNoInteractions(sessionMapper);
    }

    @Test
    @DisplayName("★ stream=false：接口固定流式，明确拒绝而不是静默按流式处理")
    void shouldRejectNonStreamRequest() {
        AiChatSendReq req = new AiChatSendReq();
        req.setContent("hi");
        req.setStream(false);

        assertThatThrownBy(() -> service.sendMessageStream(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("固定为流式返回");
        verifyNoInteractions(sessionMapper);
    }

    @Test
    @DisplayName("★ 已有会话改模型：明确拒绝（模型在会话创建时确定）")
    void shouldRejectModelSwitchOnExistingSession() {
        AiChatSendReq req = new AiChatSendReq();
        req.setSessionId(9L);
        req.setModelId(2L);
        req.setContent("hi");

        assertThatThrownBy(() -> service.sendMessageStream(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持切换模型");
        verifyNoInteractions(sessionMapper);
    }

    @Test
    @DisplayName("★ 非默认模型：明确拒绝（starter 只按租户默认模型构建 ChatClient）")
    void shouldRejectNonDefaultModel() {
        AiModelConfig defaultModel = new AiModelConfig();
        defaultModel.setId(1L);
        when(modelConfigService.getDefaultModel()).thenReturn(defaultModel);
        AiChatSendReq req = new AiChatSendReq();
        req.setModelId(2L);
        req.setContent("hi");

        assertThatThrownBy(() -> service.sendMessageStream(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅支持租户默认对话模型");
    }

    @Test
    @DisplayName("★ 创建会话指定非默认模型：明确拒绝，不写入 model_id 假值")
    void shouldRejectNonDefaultModelWhenCreatingSession() {
        AiModelConfig defaultModel = new AiModelConfig();
        defaultModel.setId(1L);
        when(modelConfigService.getDefaultModel()).thenReturn(defaultModel);
        AiChatSessionCreateReq req = new AiChatSessionCreateReq();
        req.setModelId(2L);

        assertThatThrownBy(() -> service.createSession(req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅支持租户默认对话模型");
        verifyNoInteractions(sessionMapper);
    }
}
