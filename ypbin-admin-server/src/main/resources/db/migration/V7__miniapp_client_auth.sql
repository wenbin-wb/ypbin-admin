-- =============================================================
-- 小程序登录认证方式注册
--
-- 小程序登录复用 web-admin 客户端（与服务端既有登录会话/会话策略一致），
-- 认证方式为 MINIAPP。DbLoginClientProvider 会按 sys_client.auth_types 校验认证方式，
-- 未登记 MINIAPP 时登录会被拒绝（客户端不支持当前认证方式）。
--
-- 幂等：仅在 web-admin 未包含 MINIAPP 时追加。
-- =============================================================

UPDATE sys_client
SET auth_types = CASE
        WHEN auth_types IS NULL OR auth_types = '' THEN 'MINIAPP'
        ELSE CONCAT(auth_types, ',MINIAPP')
    END
WHERE client_id = 'web-admin'
  AND (auth_types IS NULL OR auth_types NOT LIKE '%MINIAPP%');
