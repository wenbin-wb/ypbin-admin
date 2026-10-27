/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service;

import cn.ypbin.admin.ai.enums.AiDocumentStatusEnum;
import cn.ypbin.admin.ai.entity.AiDocument;
import cn.ypbin.admin.ai.entity.AiDocumentChunk;
import cn.ypbin.admin.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.ai.mapper.AiDocumentChunkMapper;
import cn.ypbin.admin.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.ai.rag.DocumentLoader;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 文档异步向量化执行器（独立 Bean，保证 `@Async` 代理生效）。
 *
 * <p>异步线程无请求上下文，租户 ID 由调用方（请求线程）显式传入并包裹执行，
 * 保证文档状态更新与知识库计数落在正确租户。</p>
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Service
@RequiredArgsConstructor
public class AiDocumentVectorizer {

    private static final Logger log = LoggerFactory.getLogger(AiDocumentVectorizer.class);

    private final AiDocumentMapper documentMapper;
    private final AiKnowledgeBaseMapper kbMapper;
    /** 单条多值 INSERT 的行数上限（分块批量写，规避 max_allowed_packet） */
    private static final int INSERT_BATCH_SIZE = 500;

    private final AiDocumentChunkMapper chunkMapper;
    private final ObjectProvider<AiRagService> ragServiceProvider;

    @Async
    public void vectorizeAsync(Long docId, Long knowledgeBaseId, Long tenantId,
            String filename, byte[] bytes) {
        TenantContext.runWithTenant(tenantId, () -> {
            AiRagService ragService = ragServiceProvider.getIfAvailable();
            if (ragService == null) {
                markDocFailed(docId, "RAG 未启用，请配置 ypbin.ai.rag.enabled=true 及向量库");
                return;
            }
            try {
                List<Document> chunks = parseAndChunk(bytes, filename, docId, knowledgeBaseId);
                ragService.ingest(String.valueOf(knowledgeBaseId), chunks);
                // 分块落库（供分块可视化）：先清历史再批量插入，保证与向量库一致
                persistChunks(docId, knowledgeBaseId, chunks);
                // 更新文档状态为"就绪"，并显式清空上次失败原因：
                // MyBatis-Plus 默认 NOT_NULL 策略会忽略实体里的 null，实体更新清不掉 error_msg。
                int readyUpdated = documentMapper.update(null,
                    new LambdaUpdateWrapper<AiDocument>()
                        .eq(AiDocument::getId, docId)
                        .set(AiDocument::getChunkCount, chunks.size())
                        .set(AiDocument::getStatus, AiDocumentStatusEnum.READY.getCode())
                        .set(AiDocument::getErrorMsg, null)
                        .set(AiDocument::getUpdateTime, LocalDateTime.now()));
                if (readyUpdated != 1) {
                    throw new IllegalStateException("文档就绪状态写入失败：docId=" + docId);
                }
                // 更新知识库文档计数（原子 SQL，避免并发读改写漂移）
                int kbUpdated = kbMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
                    .eq(AiKnowledgeBase::getId, knowledgeBaseId)
                    .setSql("doc_count = doc_count + 1"));
                if (kbUpdated != 1) {
                    // 文档本身已就绪（向量可用），这里只计数失败；不改文档状态，留 ERROR 供运维修正
                    log.error("[ypbin-ai] 知识库文档计数更新失败（知识库可能已被删除）: "
                        + "kbId={}, docId={}", knowledgeBaseId, docId);
                }
                log.debug("[ypbin-ai] 文档向量化完成: docId={}, chunks={}", docId, chunks.size());
            } catch (Exception e) {
                log.error("[ypbin-ai] 文档向量化失败: docId={}", docId, e);
                markDocFailed(docId, e.getMessage());
            }
        });
    }

    private List<Document> parseAndChunk(byte[] bytes, String filename, Long docId,
            Long knowledgeBaseId) {
        Map<String, Object> metadata = Map.of(
            "knowledgeBaseId", String.valueOf(knowledgeBaseId),
            "documentId", String.valueOf(docId),
            "filename", filename);
        return DocumentLoader.loadAndChunk(bytes, filename, metadata);
    }

    /**
     * 按固定批次批量写入分块。
     *
     * <p><b>语义变化（有意）</b>：此前逐条 {@code insert} 且本方法无事务，失败时前面已写入的分块会留下
     * （部分成功）；改为单条多值 INSERT 后一批全有全无。分块是「整份文档」的派生数据，半份分块毫无意义，
     * 故全有全无更符合语义；失败由 {@code persistChunks} 转成异常，最终把文档标为失败（不再静默）。</p>
     *
     * <p>分块数由文档大小决定（不可控），故按 {@code INSERT_BATCH_SIZE} 分块以规避
     * {@code max_allowed_packet}——这是分块批量写，不是逐行往返。</p>
     *
     * @param rows 待落库分块
     */
    private void insertChunksInBatches(List<AiDocumentChunk> rows) {
        for (int fromIndex = 0; fromIndex < rows.size(); fromIndex += INSERT_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + INSERT_BATCH_SIZE, rows.size());
            chunkMapper.insertBatch(rows.subList(fromIndex, toIndex));
        }
    }

    private void persistChunks(Long docId, Long knowledgeBaseId, List<Document> chunks) {
        try {
            chunkMapper.delete(new LambdaUpdateWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getDocumentId, docId));
            // 向量化线程内无请求上下文，租户 ID 只查一次供全部分块复用，避免循环内查库
            Long tenantId = tenantIdOf(knowledgeBaseId);
            List<AiDocumentChunk> rows = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                String text = chunk.getText() == null ? "" : chunk.getText();
                AiDocumentChunk row = new AiDocumentChunk();
                row.setTenantId(tenantId);
                row.setKnowledgeBaseId(knowledgeBaseId);
                row.setDocumentId(docId);
                row.setChunkIndex(i);
                row.setContent(text);
                row.setCharCount(text.length());
                row.setCreateTime(LocalDateTime.now());
                rows.add(row);
            }
            // 循环内只构建内存行（避免 N 次数据库往返），循环外按固定大小分块批量写
            insertChunksInBatches(rows);
            log.debug("[ypbin-ai] 分块落库完成: docId={}, chunks={}", docId, chunks.size());
        } catch (RuntimeException e) {
            // 分块落库失败不能吞：外层会把文档标为就绪并把 chunk_count 写成解析出的切片数，
            // 而实际一行分块都没落库 —— 「已就绪」的文档在分块可视化里永远是空的。
            // 交给外层 catch 统一 markDocFailed，让失败可见；重试路径会先清掉已写入的旧向量。
            log.error("[ypbin-ai] 分块落库失败，文档按失败处理: docId={}", docId, e);
            throw new IllegalStateException("分块落库失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从知识库实体取租户 ID（向量化线程内无请求上下文）。
     *
     * <p>取不到时<b>显式失败</b>：回退到 0 会把分块写进「平台租户」，
     * 既查不到也删不掉，是比任务失败更糟的静默数据污染。</p>
     */
    private Long tenantIdOf(Long knowledgeBaseId) {
        AiKnowledgeBase kb = kbMapper.selectById(knowledgeBaseId);
        if (kb == null || kb.getTenantId() == null) {
            throw new BusinessException("知识库不存在或缺少租户信息：id=" + knowledgeBaseId);
        }
        return kb.getTenantId();
    }

    private void markDocFailed(Long docId, String errorMsg) {
        AiDocument update = new AiDocument();
        update.setId(docId);
        update.setStatus(AiDocumentStatusEnum.FAILED.getCode());
        update.setErrorMsg(errorMsg != null && errorMsg.length() > 490
            ? errorMsg.substring(0, 490) : errorMsg);
        update.setUpdateTime(LocalDateTime.now());
        if (documentMapper.updateById(update) != 1) {
            log.error("[ypbin-ai] 文档失败状态写入失败: docId={}, errorMsg={}", docId, errorMsg);
        }
    }
}
