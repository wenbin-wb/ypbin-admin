/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.service.impl;

import cn.ypbin.admin.system.enums.AiDocumentStatusEnum;
import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiDocumentChunk;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.mapper.AiDocumentChunkMapper;
import cn.ypbin.admin.system.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseSaveReq;
import cn.ypbin.admin.system.ai.model.req.AiKnowledgeBaseUpdateReq;
import cn.ypbin.admin.system.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.system.ai.service.AiDocumentVectorizer;
import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.core.exception.BusinessException;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    public AiKnowledgeBase createKnowledgeBase(AiKnowledgeBaseSaveReq req) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setTenantId(currentTenantId());
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setIcon(req.getIcon());
        kb.setRemark(req.getRemark());
        kb.setDocCount(0);
        kbMapper.insert(kb);
        return kb;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeBase(Long id, AiKnowledgeBaseUpdateReq req) {
        AiKnowledgeBase kb = requireKb(id);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setIcon(req.getIcon());
        kb.setRemark(req.getRemark());
        kb.setUpdateTime(LocalDateTime.now());
        kbMapper.updateById(kb);
    }

    public List<AiKnowledgeBase> listKnowledgeBases() {
        return kbMapper.selectList(
            new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getTenantId, currentTenantId())
                .orderByDesc(AiKnowledgeBase::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long id) {
        requireKb(id);
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.delete(String.valueOf(id));
        }
        kbMapper.deleteById(id);
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
        documentMapper.insert(doc);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            markDocFailed(doc.getId(), "文件读取失败：" + e.getMessage());
            return AiDocumentVO.from(doc);
        }

        String filePath = persistOriginalFile(knowledgeBaseId, doc.getId(), filename, bytes);
        if (filePath != null) {
            AiDocument pathUpdate = new AiDocument();
            pathUpdate.setId(doc.getId());
            pathUpdate.setFilePath(filePath);
            documentMapper.updateById(pathUpdate);
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
        List<AiDocumentVO> results = new java.util.ArrayList<>();
        int failed = 0;
        for (MultipartFile file : files) {
            try {
                results.add(uploadDocument(knowledgeBaseId, file));
            } catch (Exception e) {
                failed++;
                log.warn("[ypbin-ai] 批量上传单个文件失败: filename={}", file.getOriginalFilename(), e);
            }
        }
        if (failed > 0 && results.isEmpty()) {
            throw new BusinessException("全部文件上传失败，请检查文件格式与内容");
        }
        return results;
    }

    public PageResult<AiDocumentVO> pageDocuments(Long knowledgeBaseId, PageQuery query,
            String keyword) {
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
        AiRagService ragService = ragServiceProvider.getIfAvailable();
        if (ragService != null) {
            ragService.deleteDocument(String.valueOf(knowledgeBaseId), String.valueOf(docId));
        }
        documentMapper.deleteById(docId);
        chunkMapper.delete(new LambdaQueryWrapper<AiDocumentChunk>()
            .eq(AiDocumentChunk::getDocumentId, docId));
        kbMapper.update(null, new LambdaUpdateWrapper<AiKnowledgeBase>()
            .eq(AiKnowledgeBase::getId, knowledgeBaseId)
            .gt(AiKnowledgeBase::getDocCount, 0)
            .setSql("doc_count = doc_count - 1"));
    }

    public void retryVectorize(Long knowledgeBaseId, Long docId) {
        AiDocument doc = requireDoc(knowledgeBaseId, docId);
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BusinessException("该文档未保存原文，无法重试，请删除后重新上传");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("原文件不存在（可能已被清理），请删除后重新上传");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            AiDocument update = new AiDocument();
            update.setId(docId);
            update.setStatus(AiDocumentStatusEnum.PROCESSING.getCode());
            update.setErrorMsg(null);
            update.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(update);
            documentVectorizer.vectorizeAsync(
                docId, knowledgeBaseId, doc.getTenantId(), doc.getFilename(), bytes);
        } catch (IOException e) {
            throw new BusinessException("读取原文件失败：" + e.getMessage());
        }
    }

    public String getDocumentContent(Long knowledgeBaseId, Long docId) {
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
        documentMapper.insert(doc);

        String filePath = persistOriginalFile(knowledgeBaseId, doc.getId(), filename, bytes);
        if (filePath != null) {
            AiDocument pathUpdate = new AiDocument();
            pathUpdate.setId(doc.getId());
            pathUpdate.setFilePath(filePath);
            documentMapper.updateById(pathUpdate);
        }

        documentVectorizer.vectorizeAsync(
            doc.getId(), knowledgeBaseId, doc.getTenantId(), filename, bytes);
        return AiDocumentVO.from(doc);
    }

    public AiKnowledgeBase requireKb(Long id) {
        AiKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        return kb;
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
            Path target = dir.resolve(docId + "-" + safeName);
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
        update.setStatus(2);
        update.setErrorMsg(errorMsg != null && errorMsg.length() > 490
            ? errorMsg.substring(0, 490) : errorMsg);
        update.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(update);
    }
}
