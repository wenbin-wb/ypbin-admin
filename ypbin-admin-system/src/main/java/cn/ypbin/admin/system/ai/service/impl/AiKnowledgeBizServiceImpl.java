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
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.system.ai.service.AiDocumentVectorizer;
import cn.ypbin.admin.system.ai.service.AiKnowledgeBizService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库业务实现。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeBizServiceImpl implements AiKnowledgeBizService {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeBizServiceImpl.class);

    /** 知识库问答阻塞等待上限：模型超时/挂起时不再无限占线程 */
    private static final Duration QUERY_BLOCK_TIMEOUT = Duration.ofSeconds(60);

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentVectorizer documentVectorizer;
    private final ObjectProvider<AiRagService> ragServiceProvider;
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeBase createKnowledgeBase(AiKnowledgeBaseSaveReq req) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setTenantId(currentTenantId());
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setRemark(req.getRemark());
        kb.setDocCount(0);
        kbMapper.insert(kb);
        return kb;
    }

    @Override
    public List<AiKnowledgeBase> listKnowledgeBases() {
        Long tenantId = currentTenantId();
        return kbMapper.selectList(
            new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getTenantId, tenantId)
                .orderByDesc(AiKnowledgeBase::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        requireKb(id);
        // 删向量数据
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.delete(String.valueOf(id));
        }
        // 逻辑删除知识库和文档记录
        kbMapper.deleteById(id);
        documentMapper.delete(new LambdaQueryWrapper<AiDocument>()
            .eq(AiDocument::getKnowledgeBaseId, id));
    }

    @Override
    public AiDocument uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        requireKb(knowledgeBaseId);
        Long tenantId = currentTenantId();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";

        // 先落库，状态"处理中"
        AiDocument doc = new AiDocument();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setTenantId(tenantId);
        doc.setFilename(filename);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(0);
        doc.setCreateTime(java.time.LocalDateTime.now());
        documentMapper.insert(doc);

        // 请求线程内读取字节，避免 @Async 线程中 MultipartFile 已不可读
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            markDocFailed(doc.getId(), "文件读取失败：" + e.getMessage());
            return doc;
        }
        // 异步向量化（独立 Bean 调用，保证 @Async 代理生效；仅传字节，不传 MultipartFile）
        documentVectorizer.vectorizeAsync(doc.getId(), knowledgeBaseId, tenantId, filename, bytes);

        return doc;
    }

    @Override
    public PageResult<AiDocument> pageDocuments(Long knowledgeBaseId, PageQuery query) {
        Page<AiDocument> page = documentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()),
            new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(AiDocument::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long knowledgeBaseId, Long docId) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.deleteDocument(String.valueOf(knowledgeBaseId), String.valueOf(docId));
        }
        documentMapper.deleteById(docId);
        // 更新知识库文档计数（原子 SQL，避免并发读改写漂移）
        kbMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
            .eq(AiKnowledgeBase::getId, knowledgeBaseId)
            .gt(AiKnowledgeBase::getDocCount, 0)
            .setSql("doc_count = doc_count - 1"));
    }

    @Override
    public String query(Long knowledgeBaseId, String question) {
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            return "AI 模块未启用，请配置 ypbin.ai.enabled=true";
        }
        List<String> tokens = aiChatService.chatWithKnowledge(
            "kb-query-" + knowledgeBaseId, question, String.valueOf(knowledgeBaseId))
            .collectList()
            .block(QUERY_BLOCK_TIMEOUT);
        return tokens == null ? "" : String.join("", tokens);
    }

    @Override
    public List<Map<String, Object>> searchTest(Long knowledgeBaseId, String question, int topK) {
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            return List.of();
        }
        requireKb(knowledgeBaseId);
        int k = topK > 0 && topK <= 20 ? topK : 5;
        List<Document> docs = ragService.search(
            String.valueOf(knowledgeBaseId), question, k);
        List<Map<String, Object>> result = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            Map<String, Object> item = new HashMap<>();
            item.put("content", doc.getText());
            item.put("metadata", doc.getMetadata());
            item.put("source", doc.getMetadata().get("source"));
            result.add(item);
        }
        return result;
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

    private AiKnowledgeBase requireKb(Long id) {
        AiKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        return kb;
    }

    /**
     * 当前登录用户的租户 ID；无登录上下文时明确失败，禁止静默回退默认租户。
     */
    private static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }
}