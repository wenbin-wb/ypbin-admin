-- V13: 知识库 icon 字段（emoji 或图标名，供前台卡片展示）
-- @author wenbin
-- @since 2026-08-17
ALTER TABLE ai_knowledge_base ADD COLUMN icon VARCHAR(64) NULL DEFAULT NULL COMMENT '知识库图标（emoji 或图标名）';
