-- ypbin-admin AI 模型配置：新增模型类型（对话 / 向量化）
-- 用于页面化配置 embedding 模型，替代 yml 硬编码向量化模型

ALTER TABLE ai_model_config
    ADD COLUMN model_type VARCHAR(20) NOT NULL DEFAULT 'CHAT' COMMENT '模型类型：CHAT 对话 | EMBEDDING 向量化' AFTER provider;

-- 现有模型全部视为 CHAT（兼容存量数据）
UPDATE ai_model_config SET model_type = 'CHAT' WHERE model_type IS NULL OR model_type = '';
