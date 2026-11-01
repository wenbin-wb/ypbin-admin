-- =============================================================
-- 逻辑删除后的唯一键（Flyway V4，2026-11-01）
--
-- 背景：下列唯一键都不含 is_deleted，而删除走逻辑删除（UPDATE is_deleted=1）：
--         sys_user.username、sys_role.code、sys_post.code、sys_tenant.code、
--         sys_dict.code、sys_auth_template.code、sys_client.client_id、
--         sys_user_social(platform, open_id)
--       结果是「删除后的编码/用户名被永久占用」：查重 SQL 都带 is_deleted=0，
--       预检说可用，插入却撞唯一键抛 1062；解绑第三方账号后再也绑不回去。
--       方案与 sys_space 的 code_alive 一致：未删除时生成列取原值、删除后为 NULL。
--
-- 版本号说明：V3 已被 boot-miniapp 分支的 V3__miniapp.sql 占用，主线从 V4 起编号。
--             V1__schema.sql 已同步为最终结构；本文件只服务于「已存在的库」。
-- 幂等：用 information_schema 判存在后 ALTER，重复执行安全（Flyway 只跑一次，
--       人工补跑也不会报错）。
--
-- 回滚（手工执行；执行前先清理重复数据）：
--   ALTER TABLE sys_user DROP INDEX uk_username, ADD UNIQUE KEY uk_username (username);
--   ALTER TABLE sys_user DROP COLUMN username_alive;
--   ……其余表同理把唯一键换回原列并 DROP 对应的 *_alive 列。
-- =============================================================

-- ---------- 1) sys_user.username ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'username_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_user ADD COLUMN username_alive VARCHAR(64) AS (IF(is_deleted = 0, username, NULL)) STORED COMMENT ''用户名唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user'
      AND index_name = 'uk_username' AND column_name = 'username');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_user DROP INDEX uk_username, ADD UNIQUE KEY uk_username (username_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 2) sys_role.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name = 'code_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_role ADD COLUMN code_alive VARCHAR(64) AS (IF(is_deleted = 0, code, NULL)) STORED COMMENT ''编码唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_role'
      AND index_name = 'uk_tenant_code' AND column_name = 'code');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_role DROP INDEX uk_tenant_code, ADD UNIQUE KEY uk_tenant_code (tenant_id, code_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 3) sys_post.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_post' AND column_name = 'code_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_post ADD COLUMN code_alive VARCHAR(64) AS (IF(is_deleted = 0, code, NULL)) STORED COMMENT ''编码唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_post'
      AND index_name = 'uk_tenant_code' AND column_name = 'code');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_post DROP INDEX uk_tenant_code, ADD UNIQUE KEY uk_tenant_code (tenant_id, code_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 4) sys_tenant.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_tenant' AND column_name = 'code_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_tenant ADD COLUMN code_alive VARCHAR(64) AS (IF(is_deleted = 0, code, NULL)) STORED COMMENT ''编码唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_tenant'
      AND index_name = 'uk_code' AND column_name = 'code');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_tenant DROP INDEX uk_code, ADD UNIQUE KEY uk_code (code_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 5) sys_dict.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict' AND column_name = 'code_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_dict ADD COLUMN code_alive VARCHAR(64) AS (IF(is_deleted = 0, code, NULL)) STORED COMMENT ''编码唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict'
      AND index_name = 'uk_code' AND column_name = 'code');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_dict DROP INDEX uk_code, ADD UNIQUE KEY uk_code (code_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 6) sys_auth_template.code ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_auth_template' AND column_name = 'code_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_auth_template ADD COLUMN code_alive VARCHAR(64) AS (IF(is_deleted = 0, code, NULL)) STORED COMMENT ''编码唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_auth_template'
      AND index_name = 'uk_code' AND column_name = 'code');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_auth_template DROP INDEX uk_code, ADD UNIQUE KEY uk_code (code_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 7) sys_client.client_id ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_client' AND column_name = 'client_id_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_client ADD COLUMN client_id_alive VARCHAR(64) AS (IF(is_deleted = 0, client_id, NULL)) STORED COMMENT ''客户端 ID 唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_client'
      AND index_name = 'uk_client_id' AND column_name = 'client_id');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_client DROP INDEX uk_client_id, ADD UNIQUE KEY uk_client_id (client_id_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------- 8) sys_user_social(platform, open_id) ----------
SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_social' AND column_name = 'open_id_alive');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_user_social ADD COLUMN open_id_alive VARCHAR(128) AS (IF(is_deleted = 0, open_id, NULL)) STORED COMMENT ''openId 唯一性生成列（仅供唯一键，不读）''',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @has := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user_social'
      AND index_name = 'uk_platform_open' AND column_name = 'open_id');
SET @stmt := IF(@has > 0,
    'ALTER TABLE sys_user_social DROP INDEX uk_platform_open, ADD UNIQUE KEY uk_platform_open (platform, open_id_alive)',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
