-- 微信小程序客户端（公告按客户端投放时使用）
-- 幂等：client_id 唯一键存在时跳过；固定主键冲突会显式失败，不静默跳过。
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
