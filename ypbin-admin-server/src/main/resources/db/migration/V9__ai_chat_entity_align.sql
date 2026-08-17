-- V9__ai_chat_entity_align.sql
-- 对齐 AI 对话表与 TenantBaseEntity 公共列，统一实体继承（规范修正）
-- 依赖：V8__ai_chat_engine.sql 已建表；幂等（ADD COLUMN IF NOT EXISTS）

-- ai_chat_message：补充 create_user / update_user / update_time / status
ALTER TABLE ai_chat_message
    ADD COLUMN create_user BIGINT NULL COMMENT '创建人' AFTER metadata,
    ADD COLUMN update_user BIGINT NULL COMMENT '更新人' AFTER create_user,
    ADD COLUMN update_time DATETIME NULL COMMENT '更新时间' AFTER update_user,
    MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用' AFTER update_time;

-- ai_chat_role_favorite：补充 tenant_id / create_user / create_time / update_user / update_time / status / is_deleted
ALTER TABLE ai_chat_role_favorite
    ADD COLUMN tenant_id BIGINT NULL COMMENT '租户 ID' AFTER id,
    ADD COLUMN create_user BIGINT NULL COMMENT '创建人' AFTER role_id,
    ADD COLUMN update_user BIGINT NULL COMMENT '更新人' AFTER create_user,
    ADD COLUMN update_time DATETIME NULL COMMENT '更新时间' AFTER update_user,
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用' AFTER update_time,
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER status,
    MODIFY COLUMN create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
