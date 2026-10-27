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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.ai.entity.AiDocument;
import cn.ypbin.admin.ai.entity.AiDocumentChunk;
import cn.ypbin.admin.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.ai.enums.AiDocumentStatusEnum;
import cn.ypbin.admin.ai.mapper.AiDocumentChunkMapper;
import cn.ypbin.admin.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.ai.service.AiDocumentVectorizer;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 文档「重试向量化」测试（状态守卫 + 旧向量清理）。
 *
 * <p><b>背景</b>：重试接口此前不校验文档状态，也没有清理旧数据：</p>
 * <ol>
 *   <li>已就绪的文档再点重试 → 再次 {@code ingest}，而向量库是追加语义 ⇒ 同一文档出现两套向量；</li>
 *   <li>处理中的文档再点重试 → 两个异步任务并发写；</li>
 *   <li>失败重试（典型：上次向量已写入、分块落库失败）→ 旧向量不清，检索会命中旧内容。</li>
 * </ol>
 *
 * <p>本测试锁定：只有失败态可重试；重试时先删旧向量与旧分块，再置为处理中并投递异步任务。
 * 单测不连 MySQL，{@code documentMapper} 的条件更新以桩返回 1 表示命中一行。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
class AiKnowledgeCrudComponentRetryTest {

    private static final long KB_ID = 1L;
    private static final long DOC_ID = 20L;
    private static final long TENANT_ID = 100L;

    private final AiKnowledgeBaseMapper kbMapper = mock(AiKnowledgeBaseMapper.class);
    private final AiDocumentMapper documentMapper = mock(AiDocumentMapper.class);
    private final AiDocumentChunkMapper chunkMapper = mock(AiDocumentChunkMapper.class);
    private final AiDocumentVectorizer vectorizer = mock(AiDocumentVectorizer.class);
    private final AiRagService ragService = mock(AiRagService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<AiRagService> ragServiceProvider = mock(ObjectProvider.class);

    private AiKnowledgeCrudComponent component;

    /**
     * 条件构造器需要实体的 TableInfo 缓存；单测不启动 Spring，按既有做法显式初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "retry"),
            AiDocument.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "retry"),
            AiDocumentChunk.class);
    }

    @BeforeEach
    void setUp() {
        component = new AiKnowledgeCrudComponent(kbMapper, documentMapper, chunkMapper, vectorizer,
            ragServiceProvider);
        when(kbMapper.selectById(KB_ID)).thenReturn(kb());
        when(ragServiceProvider.getIfAvailable()).thenReturn(ragService);
    }

    @Test
    @DisplayName("失败文档重试：先删旧向量与旧分块，再置处理中并投递异步向量化")
    void shouldClearOldVectorsBeforeReingest(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("a.txt"), "hello");
        when(documentMapper.selectById(DOC_ID))
            .thenReturn(doc(AiDocumentStatusEnum.FAILED, source.toString()));
        when(chunkMapper.delete(any())).thenReturn(1);
        when(documentMapper.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            component.retryVectorize(KB_ID, DOC_ID);
        }

        // 顺序即正确性：先抢占状态，再做不可回滚的删除，最后投递异步任务。
        // 反过来（先删后抢占）会让并发重试的败者删掉胜者刚写入的新向量。
        InOrder order = inOrder(documentMapper, ragService, chunkMapper, vectorizer);
        order.verify(documentMapper).update(isNull(), any());
        order.verify(ragService).deleteDocument(String.valueOf(KB_ID), String.valueOf(DOC_ID));
        order.verify(chunkMapper).delete(any());
        order.verify(vectorizer).vectorizeAsync(eq(DOC_ID), eq(KB_ID), eq(TENANT_ID),
            eq("a.txt"), any(byte[].class));
    }

    @Test
    @DisplayName("抢占成功后清理旧向量失败：回写失败状态，不投递异步任务（不能停在处理中）")
    void shouldMarkFailedWhenCleanupFails(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("a.txt"), "hello");
        when(documentMapper.selectById(DOC_ID))
            .thenReturn(doc(AiDocumentStatusEnum.FAILED, source.toString()));
        when(documentMapper.update(isNull(), any())).thenReturn(1);
        // deleteDocument 返回 void：stub 异常必须用 doThrow(...).when(...)
        doThrow(new IllegalStateException("vector store down"))
            .when(ragService).deleteDocument(String.valueOf(KB_ID), String.valueOf(DOC_ID));
        when(documentMapper.updateById(any(AiDocument.class))).thenReturn(1);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> component.retryVectorize(KB_ID, DOC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("清理旧向量失败");
        }

        ArgumentCaptor<AiDocument> failed = ArgumentCaptor.forClass(AiDocument.class);
        verify(documentMapper).updateById(failed.capture());
        assertThat(failed.getValue().getStatus()).isEqualTo(AiDocumentStatusEnum.FAILED.getCode());
        verify(vectorizer, never()).vectorizeAsync(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("已就绪文档：拒绝重试，且不碰向量库与分块（避免重复 ingest）")
    void shouldRejectRetryOnReadyDocument(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("a.txt"), "hello");
        when(documentMapper.selectById(DOC_ID))
            .thenReturn(doc(AiDocumentStatusEnum.READY, source.toString()));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> component.retryVectorize(KB_ID, DOC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已就绪");
        }

        verifyNoInteractions(ragService);
        verify(chunkMapper, never()).delete(any());
        verify(vectorizer, never()).vectorizeAsync(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("处理中文档：拒绝重试（避免并发写两套向量）")
    void shouldRejectRetryOnProcessingDocument(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("a.txt"), "hello");
        when(documentMapper.selectById(DOC_ID))
            .thenReturn(doc(AiDocumentStatusEnum.PROCESSING, source.toString()));

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> component.retryVectorize(KB_ID, DOC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在处理中");
        }

        verifyNoInteractions(ragService);
        verify(vectorizer, never()).vectorizeAsync(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RAG 未启用：提前拒绝，不删除旧分块（让数据保持原样）")
    void shouldRejectRetryWhenRagDisabled(@TempDir Path dir) throws IOException {
        Path source = Files.writeString(dir.resolve("a.txt"), "hello");
        when(documentMapper.selectById(DOC_ID))
            .thenReturn(doc(AiDocumentStatusEnum.FAILED, source.toString()));
        when(ragServiceProvider.getIfAvailable()).thenReturn(null);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getTenantId).thenReturn(Optional.of(TENANT_ID));

            assertThatThrownBy(() -> component.retryVectorize(KB_ID, DOC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RAG 未启用");
        }

        verify(chunkMapper, never()).delete(any());
        verify(vectorizer, never()).vectorizeAsync(any(), any(), any(), any(), any());
    }

    private static AiKnowledgeBase kb() {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setId(KB_ID);
        kb.setTenantId(TENANT_ID);
        return kb;
    }

    private static AiDocument doc(AiDocumentStatusEnum status, String filePath) {
        AiDocument doc = new AiDocument();
        doc.setId(DOC_ID);
        doc.setKnowledgeBaseId(KB_ID);
        doc.setTenantId(TENANT_ID);
        doc.setFilename("a.txt");
        doc.setFilePath(filePath);
        doc.setStatus(status.getCode());
        return doc;
    }
}
