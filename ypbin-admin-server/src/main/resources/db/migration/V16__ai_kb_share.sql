-- V16: ai_knowledge_base 增加公开分享配置字段
-- share_token: 唯一令牌，非空即视为启用公开分享；重置（关闭再开启）即换新令牌
-- share_expire_time: 分享过期时间，NULL 表示永不过期
-- share_password: 访问密码 SHA-256 哈希（Hex），NULL 表示无需密码
ALTER TABLE ai_knowledge_base
    ADD COLUMN share_token VARCHAR(64) DEFAULT NULL COMMENT '分享令牌（非空=启用公开分享）',
    ADD COLUMN share_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '分享是否启用 0/1',
    ADD COLUMN share_expire_time DATETIME DEFAULT NULL COMMENT '分享过期时间（NULL=永不过期）',
    ADD COLUMN share_password VARCHAR(128) DEFAULT NULL COMMENT '访问密码 SHA-256 哈希（NULL=无需密码）';

-- 唯一索引（NULL 不参与唯一约束，可多个未启用）
CREATE UNIQUE INDEX uk_ai_kb_share_token ON ai_knowledge_base (share_token);
