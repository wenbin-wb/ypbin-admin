/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.model.resp;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
/**
 * 知识库问答结果（含答案与溯源片段）。
 *
 * @author wenbin
 * @since 2026-08-17
 */
@Getter
@Setter
public class KbQueryResult {

    /** AI 生成的答案 */
    private String answer;

    /** 召回的溯源片段列表（按相关度排序） */
    private List<SourceFragment> sources;

    /**
     * 单条溯源片段。
     */
    @Getter

    @Setter
    public static class SourceFragment {

        /** 来源文档名称 */
        private String source;

        /** 片段正文内容 */
        private String content;

        /** 其它元数据（如 docId、chunkIndex 等） */
        private Map<String, Object> metadata;
    }
}
