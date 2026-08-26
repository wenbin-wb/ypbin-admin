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

import cn.ypbin.admin.system.ai.entity.AiDocument;
import cn.ypbin.admin.system.ai.entity.AiKnowledgeBase;
import cn.ypbin.admin.system.ai.mapper.AiDocumentMapper;
import cn.ypbin.admin.system.ai.mapper.AiKnowledgeBaseMapper;
import cn.ypbin.admin.system.ai.model.req.AiShareSettingReq;
import cn.ypbin.admin.system.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.system.ai.service.AiShareService;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库公开分享服务实现。
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Service
@RequiredArgsConstructor
public class AiShareServiceImpl implements AiShareService {

    private static final Logger log = LoggerFactory.getLogger(AiShareServiceImpl.class);

    private static final int TOKEN_BYTES = 16;

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final ObjectProvider<AiChatService> aiChatServiceProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String setShareSetting(Long knowledgeBaseId, AiShareSettingReq req) {
        AiKnowledgeBase kb = kbMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        // 用 UpdateWrapper 显式 set（含 null），否则 MyBatis-Plus 默认字段策略会忽略
        // null 字段，导致清空密码/令牌/过期时间不生效（残留旧值）。
        LambdaUpdateWrapper<AiKnowledgeBase> uw = new LambdaUpdateWrapper<>();
        uw.eq(AiKnowledgeBase::getId, knowledgeBaseId);
        String token = null;
        if (Boolean.TRUE.equals(req.getEnabled())) {
            // 已有令牌则保留（轮换需先关闭再开启）；无则生成
            token = kb.getShareToken() != null ? kb.getShareToken() : generateToken();
            uw.set(AiKnowledgeBase::getShareToken, token);
            uw.set(AiKnowledgeBase::getShareEnabled, 1);
            uw.set(AiKnowledgeBase::getShareExpireTime, req.getExpireTime());
            uw.set(AiKnowledgeBase::getSharePassword, encodePassword(req.getPassword()));
        } else {
            uw.set(AiKnowledgeBase::getShareToken, null);
            uw.set(AiKnowledgeBase::getShareEnabled, 0);
            uw.set(AiKnowledgeBase::getShareExpireTime, null);
            uw.set(AiKnowledgeBase::getSharePassword, null);
        }
        uw.set(AiKnowledgeBase::getUpdateTime, LocalDateTime.now());
        kbMapper.update(null, uw);
        return token;
    }

    @Override
    public Map<String, Object> getConfig(String token) {
        AiKnowledgeBase kb = requireShareKb(token);
        boolean expired = isExpired(kb);
        return Map.of(
            "name", kb.getName(),
            "description", kb.getDescription() != null ? kb.getDescription() : "",
            "icon", kb.getIcon() != null ? kb.getIcon() : "",
            "docCount", kb.getDocCount() != null ? kb.getDocCount() : 0,
            "requirePassword", kb.getSharePassword() != null && !kb.getSharePassword().isBlank(),
            "expired", expired,
            "expireTime", kb.getShareExpireTime() != null
                ? String.valueOf(kb.getShareExpireTime()) : "");
    }

    @Override
    public PageResult<AiDocumentVO> listDocuments(String token, PageQuery query,
            String password) {
        AiKnowledgeBase kb = requireValidShare(token, password);
        // 匿名请求无租户上下文：显式绑定知识库所属租户执行文档查询，否则租户插件
        // 自动追加 tenant_id=NULL 导致查不到任何记录
        return TenantContext.executeWithTenant(kb.getTenantId(), () -> {
            Page<AiDocument> page = documentMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()),
                new LambdaQueryWrapper<AiDocument>()
                    .eq(AiDocument::getKnowledgeBaseId, kb.getId())
                    .eq(AiDocument::getStatus, 1)
                    .orderByDesc(AiDocument::getCreateTime));
            List<AiDocumentVO> vos = page.getRecords().stream()
                .map(AiDocumentVO::from).toList();
            return PageResult.of(vos, page.getTotal(), page.getCurrent(), page.getSize());
        });
    }

    @Override
    public String getDocumentContent(String token, Long docId, String password) {
        AiKnowledgeBase kb = requireValidShare(token, password);
        // 匿名请求无租户上下文：显式绑定知识库所属租户查询文档，保证租户隔离完整保留
        AiDocument doc = TenantContext.executeWithTenant(kb.getTenantId(),
            () -> documentMapper.selectById(docId));
        if (doc == null || !Objects.equals(doc.getKnowledgeBaseId(), kb.getId())) {
            throw new BusinessException("文档不存在");
        }
        if (doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            throw new BusinessException("文档未落盘，无法读取内容");
        }
        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("文档文件不存在（可能已被清理）");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[ypbin-ai] 读取分享文档内容失败: docId={}", docId, e);
            throw new BusinessException("读取文档内容失败：" + e.getMessage());
        }
    }

    @Override
    public String ask(String token, String question, String password) {
        AiKnowledgeBase kb = requireValidShare(token, password);
        AiChatService aiChatService = aiChatServiceProvider.getIfAvailable();
        if (aiChatService == null) {
            throw new BusinessException("AI 对话服务未配置，请先配置对话模型");
        }
        try {
            // 匿名请求无登录上下文：显式绑定知识库所属租户，保证 RAG 检索与 AI 调用在正确租户内
            List<String> tokens = TenantContext.executeWithTenant(kb.getTenantId(),
                () -> aiChatService.chatWithKnowledge(
                        "share-" + kb.getId(), question, String.valueOf(kb.getId()))
                    .collectList()
                    .block());
            return tokens == null ? "" : String.join("", tokens);
        } catch (IllegalStateException e) {
            // 模型未配置/密钥缺失等环境问题：记录日志并向调用方暴露明确的业务错误（非静默降级）
            log.warn("[ypbin-ai] 分享问答失败: token={} err={}", token, e.getMessage());
            throw new BusinessException("AI 模型未配置，请在【AI 配置】中添加对话模型");
        }
    }

    /**
     * 校验分享令牌有效且未过期。
     *
     * <p>匿名请求无租户上下文，MyBatis-Plus 租户插件会自动追加 {@code tenant_id=NULL}，
     * 导致查不到任何记录。这里先用 {@code executeIgnore} 临时忽略租户过滤，仅凭唯一的
     * {@code shareToken} 反查知识库记录；后续读取/问答在 {@code ask} 中通过
     * {@code TenantContext.executeWithTenant(kb.tenantId)} 显式绑定租户执行，不绕过隔离。</p>
     */
    private AiKnowledgeBase requireShareKb(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("无效的分享链接");
        }
        List<AiKnowledgeBase> list = TenantContext.executeIgnore(
            () -> kbMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeBase>()
                    .eq(AiKnowledgeBase::getShareToken, token)
                    .eq(AiKnowledgeBase::getShareEnabled, 1)
                    .last("LIMIT 1")));
        if (list.isEmpty()) {
            throw new BusinessException("分享链接无效或已关闭分享");
        }
        return list.get(0);
    }

    /**
     * 校验令牌、有效期与访问密码，返回知识库。
     *
     * @param token    分享令牌
     * @param password 访问密码（无密码保护时忽略）
     */
    private AiKnowledgeBase requireValidShare(String token, String password) {
        AiKnowledgeBase kb = requireShareKb(token);
        if (isExpired(kb)) {
            throw new BusinessException("分享链接已过期，请联系分享者");
        }
        String stored = kb.getSharePassword();
        if (stored != null && !stored.isBlank()
                && !constantTimeEquals(stored, encodePassword(password))) {
            throw new BusinessException("访问密码错误");
        }
        return kb;
    }

    private boolean isExpired(AiKnowledgeBase kb) {
        return kb.getShareExpireTime() != null
            && kb.getShareExpireTime().isBefore(LocalDateTime.now());
    }

    /** 访问密码 SHA-256 哈希（Hex）；NULL 或空返回 null（不设置密码） */
    private static String encodePassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /** 常量时间比较，避免时序侧信道（用于密码哈希比对） */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
