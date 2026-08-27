-- V17: AI 检索问答日志表（统计搜索热词与问答趋势）
CREATE TABLE ai_query_log
(
    id                BIGINT       NOT NULL COMMENT '主键',
    tenant_id         INT          NOT NULL COMMENT '租户 ID',
    knowledge_base_id BIGINT       NOT NULL COMMENT '知识库 ID',
    query             VARCHAR(500) NOT NULL COMMENT '检索/问答问题',
    source            VARCHAR(20)  NOT NULL DEFAULT 'QUERY' COMMENT '来源：QUERY 问答 / SEARCH 检索测试 / RERANK 重排测试 / MULTIPLE 多库测试',
    create_user       BIGINT       NULL COMMENT '创建人',
    create_time       DATETIME     NULL COMMENT '创建时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) COMMENT 'AI 检索问答日志';

-- 热词/趋势统计按 (tenant_id, create_time) 走索引
CREATE INDEX idx_ai_query_log_tenant_time ON ai_query_log (tenant_id, create_time);
CREATE INDEX idx_ai_query_log_kb ON ai_query_log (knowledge_base_id);
