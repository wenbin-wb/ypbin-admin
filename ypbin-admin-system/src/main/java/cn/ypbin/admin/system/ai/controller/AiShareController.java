/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.controller;

import cn.ypbin.admin.system.ai.core.AiAnonymousRateLimiter;
import cn.ypbin.admin.system.ai.model.resp.AiDocumentVO;
import cn.ypbin.admin.system.ai.service.AiShareService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.tools.support.RequestUtils;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库公开分享接口（免登录，供分享链接访问者只读阅读与问答）。
 *
 * <p>通过知识库专属令牌 {@code shareToken} 鉴权，可选访问密码（请求头
 * {@code X-Share-Password}）与有效期限制；匿名用户只能访问持有令牌的知识库，
 * 无法枚举或访问其它知识库。跨域放开便于任意位置部署阅读页。</p>
 *
 * @author wenbin
 * @since 2026-08-18
 */
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")
public class AiShareController extends BaseController {

    private final AiShareService shareService;
    private final AiAnonymousRateLimiter rateLimiter;

    /** 查询分享配置（知识库名称、是否需要密码、是否过期等），供分享阅读页初始化 */
    @GetMapping("/{token}/config")
    public R<Map<String, Object>> config(@PathVariable String token) {
        return ok(shareService.getConfig(token));
    }

    /** 分页查询分享知识库的就绪文档列表 */
    @GetMapping("/{token}/documents")
    public R<PageResult<AiDocumentVO>> documents(
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Password", required = false) String password,
            PageQuery query) {
        return ok(shareService.listDocuments(token, query, password));
    }

    /** 读取分享文档原文内容 */
    @GetMapping("/{token}/documents/{docId}/content")
    public R<String> documentContent(
            @PathVariable String token,
            @PathVariable Long docId,
            @RequestHeader(value = "X-Share-Password", required = false) String password) {
        return ok(shareService.getDocumentContent(token, docId, password));
    }

    /** 对分享知识库提问（非流式，RAG 增强） */
    @PostMapping("/{token}/ask")
    public R<String> ask(
            @PathVariable String token,
            @RequestHeader(value = "X-Share-Password", required = false) String password,
            @RequestBody Map<String, String> body) {
        // 匿名公开接口限流：按客户端 IP 独立计数，防止滥用消耗模型资源
        if (!rateLimiter.tryAcquire("share:ask:" + RequestUtils.getClientIp())) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
        return ok(shareService.ask(token, body.getOrDefault("question", ""), password));
    }
}
