/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.ai.service;

import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.ai.rag.DocumentLoader;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
                // 更新文档状态为"就绪"
                AiDocument update = new AiDocument();
                update.setId(docId);
                update.setChunkCount(chunks.size());
                update.setStatus(1);
                update.setUpdateTime(java.time.LocalDateTime.now());
                documentMapper.updateById(update);
                // 更新知识库文档计数（原子 SQL，避免并发读改写漂移）
                kbMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
                    .eq(AiKnowledgeBase::getId, knowledgeBaseId)
                    .setSql("doc_count = doc_count + 1"));
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

    private void markDocFailed(Long docId, String errorMsg) {
        AiDocument update = new AiDocument();
        update.setId(docId);
        update.setStatus(2);
        update.setErrorMsg(errorMsg != null && errorMsg.length() > 490
            ? errorMsg.substring(0, 490) : errorMsg);
        update.setUpdateTime(java.time.LocalDateTime.now());
        documentMapper.updateById(update);
    }
}
