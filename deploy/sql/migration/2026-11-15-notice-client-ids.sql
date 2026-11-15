-- =============================================================
-- 公告按客户端投放（2026-11-15）：新增 sys_notice.client_ids
--
-- 值与 sys_client.client_id 对应，逗号分隔；NULL/空串表示全部客户端可见。
-- 与 deploy/sql/001-schema.sql 的 sys_notice 定义保持等价。
--
-- 执行方式：
--   mysql -uroot -p ypbin_admin < 2026-11-15-notice-client-ids.sql
--
-- 幂等：列已存在时跳过 ADD COLUMN。
--
-- 回滚：deploy/sql/rollback/2026-11-15-notice-client-ids-rollback.sql
-- =============================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_notice'
      AND column_name = 'client_ids'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_notice ADD COLUMN client_ids VARCHAR(255) NULL COMMENT ''投放客户端 ID 集合（逗号分隔）；空=全部'' AFTER cover',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
