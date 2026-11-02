-- 强制首次登录修改密码：新增 sys_user.pwd_change_required
--
-- 背景：种子里的平台超管/审批员口令是仓库内公开的演示值；仅靠文档「首登立即改密」无法约束。
-- 本列由登录链路读取：为 1 时登录响应带 mustChangePassword，网关只放行改密/登出等少数路径，
-- 改密成功或管理员重置口令后按语义写入。
--
-- 幂等：列已存在时跳过 ADD COLUMN；存量平台账号统一置 1（升级后首登强制改密）。

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'pwd_change_required'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN pwd_change_required TINYINT NOT NULL DEFAULT 0 COMMENT ''是否强制下次登录修改密码：1 是 0 否（初始口令/管理员重置时置 1）'' AFTER pwd_reset_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量平台账号：口令来源不可考（可能是公开演示口令或部署时随机口令），统一要求首登改密
UPDATE sys_user
SET pwd_change_required = 1
WHERE user_type = 'PLATFORM'
  AND is_deleted = 0;
