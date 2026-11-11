-- =============================================================
-- 增量升级：用户自助注册（2026-11-10）
--
-- 适用对象：**已经在跑的库**。全新安装不需要执行本文件——
--           deploy/sql/002-data.sql 已含同样八条系统参数。
-- 为什么单独放子目录：install.sh 用 `deploy/sql/*.sql` 通配执行初始化脚本，
--           本文件放在 migration/ 子目录内**不会**被自动执行（避免与 002 重复插入）。
--
-- 执行方式：
--   mysql -uroot -p ypbin_admin < 2026-11-10-user-register.sql
--
-- 幂等性：每条 INSERT 以 config_key 的 NOT EXISTS 守卫，**可重复执行**。
--
-- ⚠️ 为什么不能写成 `INSERT ... SELECT COALESCE(MAX(id),0)+1 ... FROM sys_config WHERE NOT EXISTS(...)`：
--    WHERE 在聚合之前生效；当参数已存在时聚合查询仍返回一行（MAX 为 NULL → id=1），
--    每次都会尝试插入 id=1 并撞主键。正确做法是先用独立 SET 语句取下一个 id。
--
-- 回滚：deploy/sql/rollback/2026-11-10-user-register-rollback.sql
--
-- 与 002-data.sql 的等价性：下面八条与 002-data.sql 的「系统参数」段**语义等价**
--         （config_key / config_value / 内置标记一致）。改一边必须同步另一边。
--
-- 安全默认：
--   · REGISTER_ENABLED 默认 false；
--   · REGISTER_DEFAULT_ROLE_CODE 默认**空**：必须由管理员显式指定一个已分配菜单权限的租户角色，
--     否则开启注册时会被服务端明确拒绝（避免注册成功但没有任何菜单的静默空系统）。
-- =============================================================

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'login', '是否开启用户自助注册', 'REGISTER_ENABLED', 'false', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'REGISTER_ENABLED');

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'login', '注册用户默认租户 ID', 'REGISTER_DEFAULT_TENANT_ID', '1', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'REGISTER_DEFAULT_TENANT_ID');

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'login', '注册用户默认角色标识', 'REGISTER_DEFAULT_ROLE_CODE', '', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'REGISTER_DEFAULT_ROLE_CODE');

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'login', '注册归属验证方式(NONE/SMS/EMAIL)', 'REGISTER_VERIFY_MODE', 'NONE', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'REGISTER_VERIFY_MODE');

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'mail', '邮箱注册验证码有效期(秒)', 'EMAIL_CODE_EXPIRE_SECONDS', '300', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'EMAIL_CODE_EXPIRE_SECONDS');

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'mail', '邮箱注册验证码发送冷却(秒)', 'EMAIL_CODE_COOLDOWN_SECONDS', '60', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'EMAIL_CODE_COOLDOWN_SECONDS');

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'login', '注册验证码每IP每分钟上限', 'REGISTER_CODE_SEND_MAX_PER_MINUTE', '5', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'REGISTER_CODE_SEND_MAX_PER_MINUTE');

SET @ypbin_next_id := (SELECT COALESCE(MAX(id), 0) + 1 FROM sys_config);
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
SELECT @ypbin_next_id, 'login', '验证码最大错误次数', 'CODE_VERIFY_MAX_ATTEMPTS', '5', 1, 1, NOW(), 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_config c WHERE c.config_key = 'CODE_VERIFY_MAX_ATTEMPTS');
