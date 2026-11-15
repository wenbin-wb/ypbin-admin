-- =============================================================
-- 回滚：移除 sys_notice.client_ids（2026-11-15）
--
-- 仅回滚列本身；已配置的公告投放客户端数据随列一起丢弃。
-- 幂等：列不存在时跳过 DROP COLUMN。
-- =============================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_notice'
      AND column_name = 'client_ids'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE sys_notice DROP COLUMN client_ids',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
