-- 回滚：移除 sys_user.pwd_change_required
--
-- 仅回滚列本身；已发生的口令变更不回滚。

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'pwd_change_required'
);
SET @ddl := IF(@col_exists = 1,
    'ALTER TABLE sys_user DROP COLUMN pwd_change_required',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
