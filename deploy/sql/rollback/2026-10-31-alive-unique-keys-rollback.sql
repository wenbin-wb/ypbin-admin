-- =============================================================
-- 回滚：逻辑删除后的唯一键（2026-10-31）
-- 回滚对象：deploy/sql/migration/2026-10-31-alive-unique-keys.sql
--
-- ⚠️ **执行前必须先清理重复数据**：升级后允许「一个已删除行 + 一个活跃行」同编码/同用户名
--    共存；回滚要把唯一键换回原列，若存在这种重复，ALTER 会失败（Duplicate entry）。
--    先跑下面的检查，返回空才能直接回滚：
--
--      SELECT username, COUNT(*) FROM sys_user GROUP BY username HAVING COUNT(*) > 1;
--      SELECT tenant_id, code, COUNT(*) FROM sys_role GROUP BY tenant_id, code HAVING COUNT(*) > 1;
--      SELECT tenant_id, code, COUNT(*) FROM sys_post GROUP BY tenant_id, code HAVING COUNT(*) > 1;
--      SELECT code, COUNT(*) FROM sys_tenant GROUP BY code HAVING COUNT(*) > 1;
--      SELECT code, COUNT(*) FROM sys_dict GROUP BY code HAVING COUNT(*) > 1;
--      SELECT code, COUNT(*) FROM sys_auth_template GROUP BY code HAVING COUNT(*) > 1;
--      SELECT client_id, COUNT(*) FROM sys_client GROUP BY client_id HAVING COUNT(*) > 1;
--      SELECT platform, open_id, COUNT(*) FROM sys_user_social GROUP BY platform, open_id HAVING COUNT(*) > 1;
--
-- 执行方式：
--   mysql -uroot -p ypbin_admin < 2026-10-31-alive-unique-keys-rollback.sql
-- 幂等性：可重复执行。
-- =============================================================

-- ---------- 1) sys_user.username ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user'
      AND index_name = 'uk_username' AND column_name = 'username_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_user DROP INDEX uk_username, ADD UNIQUE KEY uk_username (username)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'username_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_user DROP COLUMN username_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2) sys_role.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_role'
      AND index_name = 'uk_tenant_code' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_role DROP INDEX uk_tenant_code, ADD UNIQUE KEY uk_tenant_code (tenant_id, code)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_role DROP COLUMN code_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 3) sys_post.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_post'
      AND index_name = 'uk_tenant_code' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_post DROP INDEX uk_tenant_code, ADD UNIQUE KEY uk_tenant_code (tenant_id, code)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_post' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_post DROP COLUMN code_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 4) sys_tenant.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_tenant'
      AND index_name = 'uk_code' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_tenant DROP INDEX uk_code, ADD UNIQUE KEY uk_code (code)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_tenant' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_tenant DROP COLUMN code_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 5) sys_dict.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict'
      AND index_name = 'uk_code' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_dict DROP INDEX uk_code, ADD UNIQUE KEY uk_code (code)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_dict DROP COLUMN code_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 6) sys_auth_template.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_auth_template'
      AND index_name = 'uk_code' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_auth_template DROP INDEX uk_code, ADD UNIQUE KEY uk_code (code)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_auth_template' AND column_name = 'code_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_auth_template DROP COLUMN code_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 7) sys_client.client_id ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_client'
      AND index_name = 'uk_client_id' AND column_name = 'client_id_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_client DROP INDEX uk_client_id, ADD UNIQUE KEY uk_client_id (client_id)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_client' AND column_name = 'client_id_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_client DROP COLUMN client_id_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 8) sys_user_social(platform, open_id) ----------
SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_social'
      AND index_name = 'uk_platform_open' AND column_name = 'open_id_alive');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_user_social DROP INDEX uk_platform_open, ADD UNIQUE KEY uk_platform_open (platform, open_id)', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_social' AND column_name = 'open_id_alive');
SET @stmt := IF(@has > 0, 'ALTER TABLE sys_user_social DROP COLUMN open_id_alive', 'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
