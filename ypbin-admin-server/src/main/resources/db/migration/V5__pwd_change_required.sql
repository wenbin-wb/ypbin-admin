-- =============================================================
-- 强制首次登录修改密码（Flyway V5，2026-11-02）
--
-- 背景：种子里的平台超管/审批员口令是仓库内公开的演示值；仅靠文档「首登立即改密」无法约束。
-- 本列由登录链路读取：为 1 时登录响应带 mustChangePassword，拦截器只放行改密/登出等少数路径，
-- 改密成功或管理员重置口令后按语义写入。
--
-- 版本号说明：V1__schema.sql 已同步为最终结构；本文件只服务于「已存在的库」。
-- 幂等：用 information_schema 判存在后 ALTER，重复执行安全（Flyway 只跑一次，人工补跑也不会报错）。
--
-- 回滚（手工执行）：
--   ALTER TABLE sys_user DROP COLUMN pwd_change_required;
-- =============================================================

SET @has := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'pwd_change_required');
SET @stmt := IF(@has = 0,
    'ALTER TABLE sys_user ADD COLUMN pwd_change_required TINYINT NOT NULL DEFAULT 0 COMMENT ''是否强制下次登录修改密码：1 是 0 否（初始口令/管理员重置时置 1）'' AFTER pwd_reset_time',
    'DO 0');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 存量平台账号：口令来源不可考（可能是公开演示口令或部署时随机口令），统一要求首登改密
UPDATE sys_user
SET pwd_change_required = 1
WHERE user_type = 'PLATFORM'
  AND is_deleted = 0;
