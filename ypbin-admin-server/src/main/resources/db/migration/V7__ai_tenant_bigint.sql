-- AI 表租户字段类型统一：INT → BIGINT（与 sys_* 表 TenantBaseEntity 的 Long tenantId 对齐）
-- 依赖：V3__ai_schema.sql 已建表；开发/生产库均需执行，幂等（MODIFY 可重复执行）

ALTER TABLE ai_model_config MODIFY tenant_id BIGINT NOT NULL COMMENT '租户 ID';
ALTER TABLE ai_conversation MODIFY tenant_id BIGINT NOT NULL COMMENT '租户 ID';
ALTER TABLE ai_message MODIFY tenant_id BIGINT NOT NULL COMMENT '租户 ID';
ALTER TABLE ai_knowledge_base MODIFY tenant_id BIGINT NOT NULL COMMENT '租户 ID';
ALTER TABLE ai_document MODIFY tenant_id BIGINT NOT NULL COMMENT '租户 ID';
ALTER TABLE ai_prompt_template MODIFY tenant_id BIGINT NOT NULL COMMENT '租户 ID';
ALTER TABLE ai_usage_log MODIFY tenant_id BIGINT NOT NULL COMMENT '租户 ID';
