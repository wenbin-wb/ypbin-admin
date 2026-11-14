-- =============================================================
-- 小程序登录客户端
--
-- 小程序原先复用 web-admin 客户端：Token 有效期、多端登录限制只能和后台共用一套，
-- 在线用户、登录日志也分不出终端。这里为小程序登记独立客户端：
--   client_type = MINI，auth_types = MINIAPP；策略初值与 web-admin 一致，后续可在
--   「系统管理 → 客户端」里单独调整。
--
-- 幂等与失败可见：
--   1) 已存在同名客户端（含逻辑删除行）时不再插入，只把该行恢复为启用；
--   2) 若固定主键 id=2 已被其它客户端占用，INSERT 会因主键冲突直接失败——
--      宁可迁移显式报错，也不要静默跳过（否则代码按 clientId 查不到客户端，小程序无法登录）。
-- 注意：本迁移必须先于新版本代码生效。
-- =============================================================

INSERT INTO sys_client
    (id, client_id, client_type, auth_types, timeout, active_timeout, concurrent_enabled,
     remark, create_user, create_time, status, is_deleted)
SELECT 2, 'miniapp', 'MINI', 'MINIAPP', 86400, 1800, 1,
       '微信小程序登录客户端', 1, NOW(), 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_client WHERE client_id = 'miniapp');

UPDATE sys_client
SET is_deleted = 0,
    status = 1
WHERE client_id = 'miniapp';
