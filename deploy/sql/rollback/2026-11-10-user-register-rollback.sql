-- =============================================================
-- 回滚：用户自助注册的系统参数
-- 回滚对象：deploy/sql/migration/2026-11-10-user-register.sql
--           （等价于 002-data.sql「系统参数」段的 12/13/14 三条）
--
-- 执行方式：
--   mysql -uroot -p ypbin_admin < 2026-11-10-user-register-rollback.sql
--
-- ⚠️ 只删参数，不删已注册用户：已创建的账号与角色关联继续有效，
--    如需清理须另行按用户名/创建时间人工核对（避免误删真实账号）。
--
-- 幂等性：按 config_key 删除，可重复执行。
-- =============================================================

DELETE FROM sys_config
WHERE config_key IN ('REGISTER_ENABLED', 'REGISTER_DEFAULT_TENANT_ID', 'REGISTER_DEFAULT_ROLE_CODE',
                     'REGISTER_VERIFY_MODE', 'EMAIL_CODE_EXPIRE_SECONDS', 'EMAIL_CODE_COOLDOWN_SECONDS',
                     'REGISTER_CODE_SEND_MAX_PER_MINUTE', 'CODE_VERIFY_MAX_ATTEMPTS');
