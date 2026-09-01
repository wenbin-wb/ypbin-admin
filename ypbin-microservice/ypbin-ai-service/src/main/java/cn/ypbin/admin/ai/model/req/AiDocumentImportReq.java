/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.model.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库文档 URL 导入请求。
 *
 * <p>支持三种模式：
 * <ul>
 *   <li>URL — 单页抓取，{@code url} 非空</li>
 *   <li>SITEMAP — 批量抓取，{@code url} 指向 sitemap.xml，{@code maxUrls} 控制上限</li>
 *   <li>RSS — 订阅源抓取，{@code url} 指向 RSS/Atom feed</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Getter
@Setter
public class AiDocumentImportReq {

    /** 来源类型，必须为 URL / SITEMAP / RSS */
    @NotBlank
    private String sourceType;

    /** 要抓取的 URL 地址（或 Sitemap / RSS Feed 地址） */
    @NotBlank
    private String url;

    /**
     * Sitemap 模式：每次最多抓取的 URL 数量，1–100，默认 10。
     * URL / RSS 模式时忽略。
     */
    private Integer maxUrls = 10;

    /**
     * 批量导入时每条 URL 的自定义标题前缀，可空。
     * URL 模式时作为文档标题使用（空则取页面 title）。
     */
    @Size(max = 200)
    private String customTitle;
}
