-- V15: ai_knowledge_base 增加网页挂件配置字段
-- widget_token: 唯一令牌，非空即视为启用公开挂件问答；重置即换新令牌
ALTER TABLE ai_knowledge_base
    ADD COLUMN widget_token VARCHAR(64) DEFAULT NULL COMMENT '网页挂件令牌（非空=启用公开问答）',
    ADD COLUMN widget_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '挂件是否启用 0/1';

-- 唯一索引（NULL 不参与唯一约束，可多个未启用）
CREATE UNIQUE INDEX uk_ai_kb_widget_token ON ai_knowledge_base (widget_token);
