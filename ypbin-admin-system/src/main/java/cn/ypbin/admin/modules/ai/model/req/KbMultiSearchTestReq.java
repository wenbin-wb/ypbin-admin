/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.model.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 多知识库联合检索测试入参。
 *
 * @author wenbin
 * @since 2026-08-28
 */
@Getter
@Setter
public class KbMultiSearchTestReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标知识库 ID 列表。
     */
    @NotEmpty(message = "知识库列表不能为空")
    private List<Long> knowledgeBaseIds;

    /**
     * 检索问题。
     */
    @NotBlank(message = "检索问题不能为空")
    private String question;

    /**
     * 每个知识库 Top-K 条数。
     */
    @Min(value = 1, message = "Top-K 最小为 1")
    @Max(value = 50, message = "Top-K 最大为 50")
    private Integer topKPerKb = 5;

    public List<Long> getKnowledgeBaseIds() {
        return knowledgeBaseIds;
    }

    public void setKnowledgeBaseIds(List<Long> knowledgeBaseIds) {
        this.knowledgeBaseIds = knowledgeBaseIds;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
