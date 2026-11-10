-- =============================================================
-- 注册归属验证的系统参数（2026-11-10）
--
-- 为什么单独一个版本而不是改 V2/V5：Flyway 开启 validate-on-migrate，
-- 已执行过的版本再改内容会 checksum mismatch 导致启动失败。新增参数一律走新版本。
--
-- 幂等性：每条 INSERT ... SELECT 以 config_key 的 NOT EXISTS 守卫；
--         id 取 `MAX(id)+1`，避免与已有人工导入的参数主键冲突。
--
-- 安全默认：
--   · REGISTER_VERIFY_MODE 默认 NONE；
--   · 清空该值不等于 NONE（服务端按非法配置显式拒绝），只有「键不存在」才取默认。
-- =============================================================

INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT COALESCE(MAX(id), 0) + 1, 'login', '注册归属验证方式(NONE/SMS/EMAIL)', 'REGISTER_VERIFY_MODE', 'NONE', 1, 1, NOW(), 1, 0
FROM sys_config
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'REGISTER_VERIFY_MODE');

INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT COALESCE(MAX(id), 0) + 1, 'mail', '邮箱注册验证码有效期(秒)', 'EMAIL_CODE_EXPIRE_SECONDS', '300', 1, 1, NOW(), 1, 0
FROM sys_config
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'EMAIL_CODE_EXPIRE_SECONDS');

INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT COALESCE(MAX(id), 0) + 1, 'mail', '邮箱注册验证码发送冷却(秒)', 'EMAIL_CODE_COOLDOWN_SECONDS', '60', 1, 1, NOW(), 1, 0
FROM sys_config
WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'EMAIL_CODE_COOLDOWN_SECONDS');
