-- =============================================================
-- 收紧 web-admin 的认证方式
--
-- 1) 移除 MINIAPP：小程序已有独立的 miniapp 客户端，且登录入口由服务端绑定
--    （ypbin.admin.login-client.routes）。保留 MINIAPP 会让「路由漏配」的异常情况
--    静默回落到 web-admin 并登录成功，而不是 fail-closed。
-- 2) 追加 SOCIAL：第三方登录回调已按路由绑定到 web-admin；认证方式白名单缺少 SOCIAL
--    时，配置了社交平台后登录会被拒。是否真正可用仍由社交平台配置决定。
--
-- 幂等：仅在需要时修改。
-- =============================================================

UPDATE sys_client
SET auth_types = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', auth_types, ','), ',MINIAPP,', ','))
WHERE client_id = 'web-admin'
  AND auth_types LIKE '%MINIAPP%';

UPDATE sys_client
SET auth_types = CASE
        WHEN auth_types IS NULL OR auth_types = '' THEN 'SOCIAL'
        ELSE CONCAT(auth_types, ',SOCIAL')
    END
WHERE client_id = 'web-admin'
  AND (auth_types IS NULL OR auth_types NOT LIKE '%SOCIAL%');
