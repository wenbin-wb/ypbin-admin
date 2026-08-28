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

import cn.ypbin.admin.system.ai.service.AiWidgetService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.tools.limiter.RateLimit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库网页挂件公开接口（免登录，供嵌入第三方网站）。
 *
 * <p>通过知识库专属令牌 {@code widgetToken} 鉴权：匿名用户只能对持有令牌的知识库
 * 读取配置与提问，无法枚举或访问其它知识库。跨域全放开以便任意站点嵌入。</p>
 *
 * @author wenbin
 * @since 2026-08-18
 */
@RestController
@RequestMapping("/widget")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")
public class AiWidgetController extends BaseController {

    private final AiWidgetService widgetService;

    /** 查询挂件配置（知识库名称等），供挂件脚本初始化 */
    @GetMapping("/{token}/config")
    public R<Map<String, Object>> config(@PathVariable String token) {
        return ok(widgetService.getConfig(token));
    }

    /** 匿名提问（基于该知识库 RAG 增强）；按客户端 IP 限流，防止滥用消耗模型资源 */
    @PostMapping("/{token}/ask")
    @RateLimit(key = "widget:ask", window = 60, count = 10, message = "请求过于频繁，请稍后再试")
    public R<String> ask(@PathVariable String token,
            @RequestBody Map<String, String> body) {
        return ok(widgetService.ask(token, body.getOrDefault("question", "")));
    }

    /**
     * 挂件嵌入脚本（免登录，无需令牌）。从 classpath 静态资源读取返回，
     * 供第三方站点 <script src="host/widget/embed.js"> 引用。
     */
    @GetMapping(value = "/embed.js", produces = "application/javascript")
    public String embedJs() {
        try {
            return new ClassPathResource("static/embed.js")
                .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("挂件脚本缺失：static/embed.js", e);
        }
    }
}
