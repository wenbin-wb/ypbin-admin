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

import cn.ypbin.admin.ai.model.req.AiShareSettingReq;
import cn.ypbin.admin.ai.model.resp.AiDocumentVO;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import java.util.Map;

/**
 * 知识库公开分享服务。
 *
 * <p>对外提供免登录的只读阅读与问答通道：通过知识库专属令牌（{@code shareToken}）鉴权，
 * 可选访问密码与有效期控制。内部通过 {@code TenantContext} 将请求限定在知识库所属租户，
 * 不泄露其它租户数据，也不绕过租户隔离。</p>
 *
 * @author wenbin
 * @since 2026-08-18
 */
public interface AiShareService {

    /**
     * 保存知识库分享设置。
     *
     * <p>启用时若尚无令牌则生成新令牌，否则保留原令牌并更新有效期/密码；
     * 停用时清除全部分享配置。</p>
     *
     * @param knowledgeBaseId 知识库 ID
     * @param req             分享设置（enabled/expireTime/password）
     * @return 启用时返回分享令牌，停用返回 null
     */
    String setShareSetting(Long knowledgeBaseId, AiShareSettingReq req);

    /**
     * 按令牌查询分享配置（知识库名称、是否需要密码、是否过期等）。
     * 不校验访问密码，仅返回配置信息供访问方初始化。
     *
     * @param token 分享令牌
     * @return 配置映射（name/description/icon/docCount/requirePassword/expired 等）
     */
    Map<String, Object> getConfig(String token);

    /**
     * 分页查询分享知识库的就绪文档列表（不含内部路径）。
     *
     * @param token    分享令牌
     * @param query    分页参数
     * @param password 访问密码（未设置密码时传 null）
     * @return 文档分页结果
     */
    PageResult<AiDocumentVO> listDocuments(String token, PageQuery query, String password);

    /**
     * 读取分享文档原文内容。
     *
     * @param token    分享令牌
     * @param docId    文档 ID
     * @param password 访问密码（未设置密码时传 null）
     * @return 文档原文
     */
    String getDocumentContent(String token, Long docId, String password);

    /**
     * 对分享知识库提问（非流式，RAG 增强）。
     *
     * @param token    分享令牌
     * @param question 问题
     * @param password 访问密码（未设置密码时传 null）
     * @return AI 回答文本
     */
    String ask(String token, String question, String password);
}
