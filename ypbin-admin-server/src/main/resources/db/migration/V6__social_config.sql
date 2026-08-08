-- =============================================================
-- 第三方登录平台启用状态与支付宝公钥
-- 升级后所有平台默认关闭，完成配置并显式启用后才注册。
-- =============================================================

INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (68, 'social', 'GitHub 是否启用', 'SOCIAL_GITHUB_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (69, 'social', 'Gitee 是否启用', 'SOCIAL_GITEE_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (70, 'social', 'QQ 是否启用', 'SOCIAL_QQ_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (71, 'social', '微信开放平台是否启用', 'SOCIAL_WECHAT_OPEN_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (72, 'social', '支付宝是否启用', 'SOCIAL_ALIPAY_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (73, 'social', '钉钉是否启用', 'SOCIAL_DINGTALK_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (74, 'social', '支付宝公钥', 'SOCIAL_ALIPAY_PUBLIC_KEY', '', 1, 1, NOW(), 1, 0);
