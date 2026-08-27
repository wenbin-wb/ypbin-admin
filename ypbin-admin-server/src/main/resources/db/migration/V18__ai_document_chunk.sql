-- V18: 文档分块表（向量化时落库，用于分块可视化与诊断）
CREATE TABLE ai_document_chunk
(
    id                BIGINT       NOT NULL COMMENT '主键',
    tenant_id         INT          NOT NULL COMMENT '租户 ID',
    knowledge_base_id BIGINT       NOT NULL COMMENT '所属知识库',
    document_id       BIGINT       NOT NULL COMMENT '所属文档',
    chunk_index       INT          NOT NULL COMMENT '分块序号（0 起）',
    content           TEXT         NOT NULL COMMENT '分块内容',
    char_count        INT          NOT NULL DEFAULT 0 COMMENT '字符数',
    create_user       BIGINT       NULL COMMENT '创建人',
    create_time       DATETIME     NULL COMMENT '创建时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) COMMENT 'AI 文档分块';

CREATE INDEX idx_ai_chunk_doc ON ai_document_chunk (document_id, chunk_index);
CREATE INDEX idx_ai_chunk_kb ON ai_document_chunk (knowledge_base_id);
