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

import cn.ypbin.admin.ai.enums.AiDocumentStatusEnum;
import cn.ypbin.admin.ai.entity.AiDocument;
import cn.ypbin.admin.ai.entity.AiDocumentChunk;
import cn.ypbin.admin.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.ai.mapper.AiDocumentChunkMapper;
import cn.ypbin.admin.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.ai.model.req.AiKnowledgeBaseUpdateReq;
import cn.ypbin.admin.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.ai.model.resp.AiKnowledgeBaseResp;
import cn.ypbin.admin.ai.service.AiDocumentVectorizer;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库 CRUD 与文档管理组件。
 *
 * <p>承载知识库的增删改查与文档上传/分页/删除/重试向量化等管理能力，
 * 从 {@link AiKnowledgeBizServiceImpl} 拆分，保持单一职责。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class AiKnowledgeCrudComponent {

    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeCrudComponent.class);

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiDocumentVectorizer documentVectorizer;
    private final ObjectProvider<AiRagService> ragServiceProvider;

    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeBaseResp createKnowledgeBase(AiKnowledgeBaseSaveReq req) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setTenantId(currentTenantId());
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setIcon(req.getIcon());
        kb.setRemark(req.getRemark());
        kb.setDocCount(0);
        if (kbMapper.insert(kb) != 1) {
            throw new BusinessException("知识库创建失败");
        }
        return toResp(kb);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBase(Long id, AiKnowledgeBaseUpdateReq req) {
        AiKnowledgeBase kb = requireKb(id);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setIcon(req.getIcon());
        kb.setRemark(req.getRemark());
        kb.setUpdateTime(LocalDateTime.now());
        if (kbMapper.updateById(kb) != 1) {
            throw new BusinessException("知识库更新失败");
        }
    }

    public List<AiKnowledgeBaseResp> listKnowledgeBases() {
        return kbMapper.selectList(
            new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getTenantId, currentTenantId())
                .orderByDesc(AiKnowledgeBase::getCreateTime))
            .stream().map(this::toResp).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        requireKb(id);
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.delete(String.valueOf(id));
        }
        if (kbMapper.deleteById(id) != 1) {
            throw new BusinessException("知识库删除失败");
        }
        documentMapper.delete(new LambdaQueryWrapper<AiDocument>()
            .eq(AiDocument::getKnowledgeBaseId, id));
        chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
            .eq(AiDocumentChunk::getKnowledgeBaseId, id));
    }

    public AiDocumentVO uploadDocument(Long knowledgeBaseId, MultipartFile file) {
        requireKb(knowledgeBaseId);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";

        AiDocument doc = new AiDocument();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setTenantId(currentTenantId());
        doc.setFilename(filename);
        doc.setFileSize(file.getSize());
        doc.setChunkCount(0);
        doc.setStatus(AiDocumentStatusEnum.PROCESSING.getCode());
        doc.setSourceType("UPLOAD");
        doc.setCreateTime(LocalDateTime.now());
        if (documentMapper.insert(doc) != 1) {
            throw new BusinessException("文档记录创建失败");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            // 同步失败状态到内存对象：否则响应仍返回 PROCESSING，而库里已是 FAILED，
            // 前端会一直转圈等待一个永远不会完成的任务。
            doc.setStatus(AiDocumentStatusEnum.FAILED.getCode());
            doc.setErrorMsg("文件读取失败：" + e.getMessage());
            markDocFailed(doc.getId(), doc.getErrorMsg());
            return AiDocumentVO.from(doc);
        }

        String filePath = persistOriginalFile(knowledgeBaseId, doc.getId(), filename, bytes);
        if (filePath != null) {
            AiDocument pathUpdate = new AiDocument();
            pathUpdate.setId(doc.getId());
            pathUpdate.setFilePath(filePath);
            if (documentMapper.updateById(pathUpdate) != 1) {
                // 原文已落盘但路径没写回：向量化仍可完成，但「重试/查看原文」会不可用，必须留痕
                log.error("[ypbin-ai] 文档路径回写失败: docId={}, filePath={}", doc.getId(), filePath);
            } else {
                doc.setFilePath(filePath);
            }
        }

        documentVectorizer.vectorizeAsync(
            doc.getId(), knowledgeBaseId, doc.getTenantId(), filename, bytes);
        return AiDocumentVO.from(doc);
    }

    public List<AiDocumentVO> batchUploadDocuments(Long knowledgeBaseId,
            MultipartFile[] files) {
        requireKb(knowledgeBaseId);
        if (files == null || files.length == 0) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (files.length > 20) {
            throw new BusinessException("单次最多上传 20 个文件");
        }
        List<AiDocumentVO> results = new ArrayList<>();
        int failed = 0;
        for (MultipartFile file : files) {
            try {
                results.add(uploadDocument(knowledgeBaseId, file));
            } catch (Exception e) {
                failed++;
                // 批量容错必须记 ERROR + 完整堆栈：WARN 在默认级别下常被过滤，事后无法定位是哪个文件
                log.error("[ypbin-ai] 批量上传单个文件失败: filename={}",
                    LogSanitizer.sanitize(file.getOriginalFilename()), e);
            }
        }
        if (failed > 0 && results.isEmpty()) {
            throw new BusinessException("全部文件上传失败，请检查文件格式与内容");
        }
        return results;
    }

    public PageResult<AiDocumentVO> pageDocuments(Long knowledgeBaseId, PageQuery query,
            String keyword) {
        requireKb(knowledgeBaseId);
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
            .eq(AiDocument::getKnowledgeBaseId, knowledgeBaseId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AiDocument::getFilename, keyword.trim());
        }
        wrapper.orderByDesc(AiDocument::getCreateTime);
        Page<AiDocument> page = documentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        List<AiDocumentVO> vos = page.getRecords().stream()
            .map(AiDocumentVO::from).toList();
        return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long knowledgeBaseId, Long docId) {
        // 先校验知识库归属与「文档确实属于该知识库」：否则可传 (kbA, kbB 的 docId)
        // 删掉另一个库的文档并扣错 A 库的 doc_count；RAG 删除不经租户过滤，跨租户时还会误删向量。
        requireKb(knowledgeBaseId);
        requireDoc(knowledgeBaseId, docId);
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.deleteDocument(String.valueOf(knowledgeBaseId), String.valueOf(docId));
        }
        chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
            .eq(AiDocumentChunk::getDocumentId, docId));
        if (documentMapper.deleteById(docId) != 1) {
            throw new BusinessException("文档删除失败");
        }
        kbMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
            .eq(AiKnowledgeBase::getId, knowledgeBaseId)
            .gt(AiKnowledgeBase::getDocCount, 0)
            .setSql("doc_count = doc_count - 1"));
    }

    public void retryVectorize(Long knowledgeBaseId, Long docId) {
        requireKb(knowledgeBaseId);
        AiDocument doc = requireDoc(knowledgeBaseId, docId);
        // 只有失败的文档才需要重试：处理中再点一次会并发写两套向量；
        // 已就绪再点会重复 ingest（向量库按追加语义，旧向量不会被覆盖）。
        if (AiDocumentStatusEnum.PROCESSING.getCode().equals(doc.getStatus())) {
            throw new BusinessException("文档正在处理中，请稍候再试");
        }
        if (AiDocumentStatusEnum.READY.getCode().equals(doc.getStatus())) {
            throw new BusinessException("文档已就绪，无需重试");
        }
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BusinessException("该文档未保存原文，无法重试，请删除后重新上传");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("原文件不存在（可能已被清理），请删除后重新上传");
        }
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService == null) {
            // 没有向量库时重试必然再次失败；提前拒绝，也避免白删旧分块
            throw new BusinessException("RAG 未启用，请先配置向量库后再重试");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            // 顺序至关重要：**先抢占状态，再做不可回滚的删除**。
            // 若先删向量再 CAS，两个并发重试都可能进入删除段：败者会把胜者刚 ingest 的
            // 新向量删掉，自己再因 CAS 失败退出——文档 READY 却一条向量都不剩。
            // 必须用 UpdateWrapper 显式 set(null)：MyBatis-Plus 默认 NOT_NULL 更新策略会忽略
            // 实体里的 null，旧 error_msg 会残留到重试成功后，表现为「已就绪」却仍展示上次错误。
            int affected = documentMapper.update(null, new LambdaUpdateWrapper<AiDocument>()
                .eq(AiDocument::getId, docId)
                .ne(AiDocument::getStatus, AiDocumentStatusEnum.PROCESSING.getCode())
                .ne(AiDocument::getStatus, AiDocumentStatusEnum.READY.getCode())
                .set(AiDocument::getStatus, AiDocumentStatusEnum.PROCESSING.getCode())
                .set(AiDocument::getErrorMsg, null)
                .set(AiDocument::getUpdateTime, LocalDateTime.now()));
            if (affected != 1) {
                throw new BusinessException("文档状态已变更（处理中或已就绪），请刷新后重试");
            }
            // 抢占成功后才清旧向量与旧分块：ingest 是追加语义，不清会让新旧两套向量
            // 同时被检索命中；分块表「先清后插」也无法覆盖向量库里的旧数据
            try {
                ragService.deleteDocument(String.valueOf(knowledgeBaseId), String.valueOf(docId));
                chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
                    .eq(AiDocumentChunk::getDocumentId, docId));
            } catch (RuntimeException e) {
                // 状态已置为处理中，但清理失败：必须回写失败，否则永远停在「处理中」
                log.error("[ypbin-ai] 重试前清理旧向量失败: docId={}", docId, e);
                markDocFailed(docId, "清理旧向量失败：" + e.getMessage());
                throw new BusinessException("清理旧向量失败，请稍后重试");
            }
            documentVectorizer.vectorizeAsync(
                docId, knowledgeBaseId, doc.getTenantId(), doc.getFilename(), bytes);
        } catch (IOException e) {
            throw new BusinessException("读取原文件失败：" + e.getMessage());
        }
    }

    public String getDocumentContent(Long knowledgeBaseId, Long docId) {
        requireKb(knowledgeBaseId);
        AiDocument doc = requireDoc(knowledgeBaseId, docId);
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BusinessException("文档未落盘，无法读取内容");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("文档文件不存在（可能已被清理）");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[ypbin-ai] 读取文档内容失败: docId={}", docId, e);
            throw new BusinessException("读取文档内容失败：" + e.getMessage());
        }
    }

    public List<Map<String, Object>> listDocumentChunks(Long knowledgeBaseId, Long docId) {
        requireKb(knowledgeBaseId);
        requireDoc(knowledgeBaseId, docId);
        List<AiDocumentChunk> chunks = chunkMapper.selectList(
            new LambdaQueryWrapper<AiDocumentChunk>()
                .eq(AiDocumentChunk::getKnowledgeBaseId, knowledgeBaseId)
                .eq(AiDocumentChunk::getDocumentId, docId)
                .orderByAsc(AiDocumentChunk::getChunkIndex));
        return chunks.stream()
            .map(c -> Map.<String, Object>of(
                "chunkIndex", c.getChunkIndex(),
                "content", c.getContent() == null ? "" : c.getContent(),
                "charCount", c.getCharCount() != null ? c.getCharCount() : 0))
            .toList();
    }

    /** 供导入组件复用：将文本内容落库并异步向量化 */
    public AiDocumentVO createDocFromText(Long knowledgeBaseId, String filename,
            String sourceUrl, String sourceType, byte[] bytes) {
        AiDocument doc = new AiDocument();
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setTenantId(currentTenantId());
        doc.setFilename(filename);
        doc.setFileSize((long) bytes.length);
        doc.setChunkCount(0);
        doc.setStatus(AiDocumentStatusEnum.PROCESSING.getCode());
        doc.setSourceType(sourceType);
        doc.setSourceUrl(sourceUrl);
        doc.setCreateTime(LocalDateTime.now());
        if (documentMapper.insert(doc) != 1) {
            throw new BusinessException("文档记录创建失败");
        }

        String filePath = persistOriginalFile(knowledgeBaseId, doc.getId(), filename, bytes);
        if (filePath != null) {
            AiDocument pathUpdate = new AiDocument();
            pathUpdate.setId(doc.getId());
            pathUpdate.setFilePath(filePath);
            if (documentMapper.updateById(pathUpdate) != 1) {
                log.error("[ypbin-ai] 导入文档路径回写失败: docId={}, filePath={}",
                    doc.getId(), filePath);
            } else {
                doc.setFilePath(filePath);
            }
        }

        documentVectorizer.vectorizeAsync(
            doc.getId(), knowledgeBaseId, doc.getTenantId(), filename, bytes);
        return AiDocumentVO.from(doc);
    }

    public AiKnowledgeBaseResp toResp(AiKnowledgeBase kb) {
        AiKnowledgeBaseResp resp = new AiKnowledgeBaseResp();
        BeanUtils.copyProperties(kb, resp);
        return resp;
    }

    /**
     * 校验知识库存在且归属当前租户（防跨租户访问）。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     */
    public AiKnowledgeBase requireKb(Long id) {
        if (id == null) {
            throw new BusinessException("知识库 ID 不能为空");
        }
        AiKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        Long tenantId = currentTenantId();
        if (!tenantId.equals(kb.getTenantId())) {
            throw new BusinessException("无权访问该知识库");
        }
        return kb;
    }

    /**
     * 批量校验多个知识库均存在且归属当前租户（一次 SQL 批量查询，禁逐项循环查库）。
     * 任一项不存在或跨租户即整体拒绝。
     *
     * @param ids 知识库 ID 集合
     */
    public void requireKbs(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("知识库 ID 不能为空");
        }
        Set<Long> distinctIds = new HashSet<>(ids);
        if (distinctIds.contains(null)) {
            throw new BusinessException("知识库 ID 不能为空");
        }
        List<AiKnowledgeBase> kbs = kbMapper.selectByIds(distinctIds);
        if (kbs.size() != distinctIds.size()) {
            throw new BusinessException("知识库不存在");
        }
        Long tenantId = currentTenantId();
        boolean crossTenant = kbs.stream()
            .anyMatch(kb -> !tenantId.equals(kb.getTenantId()));
        if (crossTenant) {
            throw new BusinessException("无权访问该知识库");
        }
    }

    public AiDocument requireDoc(Long knowledgeBaseId, Long docId) {
        AiDocument doc = documentMapper.selectById(docId);
        if (doc == null || !Objects.equals(doc.getKnowledgeBaseId(), knowledgeBaseId)) {
            throw new BusinessException("文档不存在");
        }
        return doc;
    }

    public Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法获取当前租户上下文"));
    }

    private String persistOriginalFile(Long knowledgeBaseId, Long docId,
            String filename, byte[] bytes) {
        try {
            Path dir = Paths.get(System.getProperty("user.dir"),
                "data", "ai-files", String.valueOf(knowledgeBaseId));
            Files.createDirectories(dir);
            String safeName = (filename == null || filename.isBlank())
                ? "document" : filename.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path normalizedDir = dir.normalize();
            Path target = normalizedDir.resolve(docId + "-" + safeName).normalize();
            // 纵深防御：即便文件名清洗规则被绕过，也保证最终路径仍落在目标目录内（防路径穿越）
            if (!target.startsWith(normalizedDir)) {
                throw new BusinessException("文件名非法，拒绝落盘");
            }
            Files.write(target, bytes);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("[ypbin-ai] 文档原文落盘失败: docId={}", docId, e);
            return null;
        }
    }

    private void markDocFailed(Long docId, String errorMsg) {
        AiDocument update = new AiDocument();
        update.setId(docId);
        update.setStatus(AiDocumentStatusEnum.FAILED.getCode());
        update.setErrorMsg(errorMsg != null && errorMsg.length() > 490
            ? errorMsg.substring(0, 490) : errorMsg);
        update.setUpdateTime(LocalDateTime.now());
        if (documentMapper.updateById(update) != 1) {
            // 调用方已在异常路径上，这里不能再用异常覆盖原始失败原因；但必须留痕
            log.error("[ypbin-ai] 文档失败状态写入失败: docId={}, errorMsg={}", docId, errorMsg);
        }
    }
}
