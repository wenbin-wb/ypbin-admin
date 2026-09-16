/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.mapper;

import cn.ypbin.admin.ai.entity.AiDocumentChunk;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * AI 文档分块 Mapper。
 *
 * @author wenbin
 * @since 2026-08-18
 */
public interface AiDocumentChunkMapper extends BaseMapper<AiDocumentChunk> {

    /**
     * 批量插入文档分块（单条多值 INSERT）。
     *
     * <p>一份文档的分块数可达数千，逐条 insert 与分块数同阶；改为一次多值 INSERT 后事务内往返次数大幅下降。
     * 主键由 MyBatis-Plus 的 {@code IdType.ASSIGN_ID} 在参数处理阶段回填（与内置 {@code insert} 同一机制），
     * 故 SQL 里显式写 {@code id} 列也不是空值。</p>
     *
     * <p><b>刻意不写 {@code is_deleted} 列</b>：与内置 {@code insert} 的「null 列整列省略、走
     * {@code NOT NULL DEFAULT 0}」口径一致；显式绑定 null 在严格模式下会报
     * {@code 1048 Column 'is_deleted' cannot be null}。</p>
     *
     * @param rows 待插入分块（调用方保证非空）
     * @return 影响行数
     */
    @Insert("<script>"
        + "INSERT INTO ai_document_chunk"
        + " (id, tenant_id, knowledge_base_id, document_id, chunk_index, content, char_count,"
        + "  create_user, create_time, update_user, update_time, status) VALUES "
        + "<foreach collection='rows' item='r' separator=','>"
        + " (#{r.id}, #{r.tenantId}, #{r.knowledgeBaseId}, #{r.documentId}, #{r.chunkIndex}, #{r.content},"
        + "  #{r.charCount}, #{r.createUser}, #{r.createTime}, #{r.updateUser}, #{r.updateTime},"
        + "  #{r.status})"
        + "</foreach>"
        + "</script>")
    int insertBatch(@Param("rows") List<AiDocumentChunk> rows);

}
