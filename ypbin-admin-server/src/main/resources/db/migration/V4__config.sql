-- =============================================================
-- 系统参数配置（全局共享，不隔离租户）
-- =============================================================

CREATE TABLE sys_config
(
    id           BIGINT       NOT NULL COMMENT '主键',
    config_group VARCHAR(64)  NOT NULL COMMENT '参数分组',
    name         VARCHAR(128) NOT NULL COMMENT '参数名称',
    config_key   VARCHAR(128) NOT NULL COMMENT '参数键',
    config_value VARCHAR(512) NULL COMMENT '参数值',
    built_in     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否内置：1 是 0 否',
    remark       VARCHAR(255) NULL COMMENT '备注',
    create_user  BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NULL COMMENT '创建时间',
    update_user  BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NULL COMMENT '更新时间',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统参数';

-- 站点配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_time, status, is_deleted)
VALUES (1, 'site', '系统名称', 'SITE_NAME', 'ypbin-admin', 1, NOW(), 1, 0),
       (2, 'site', '版权信息', 'SITE_COPYRIGHT', 'ypbin', 1, NOW(), 1, 0);

-- 登录配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_time, status, is_deleted)
VALUES (10, 'login', '是否开启登录验证码', 'LOGIN_CAPTCHA_ENABLED', 'false', 1, NOW(), 1, 0),
       (11, 'login', '是否开启短信验证码登录', 'LOGIN_SMS_ENABLED', 'false', 1, NOW(), 1, 0);

-- 密码策略配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_time, status, is_deleted)
VALUES (20, 'password', '密码最小长度', 'PASSWORD_MIN_LENGTH', '8', 1, NOW(), 1, 0),
       (21, 'password', '是否必须含数字', 'PASSWORD_REQUIRE_DIGIT', 'true', 1, NOW(), 1, 0),
       (22, 'password', '是否必须含字母', 'PASSWORD_REQUIRE_LETTER', 'true', 1, NOW(), 1, 0),
       (23, 'password', '是否必须含特殊字符', 'PASSWORD_REQUIRE_SYMBOL', 'false', 1, NOW(), 1, 0),
       (24, 'password', '是否允许含用户名', 'PASSWORD_ALLOW_CONTAIN_USERNAME', 'false', 1, NOW(), 1, 0),
       (25, 'password', '登录错误锁定阈值', 'PASSWORD_ERROR_LOCK_COUNT', '5', 1, NOW(), 1, 0),
       (26, 'password', '账号锁定时长(分钟)', 'PASSWORD_LOCK_MINUTES', '15', 1, NOW(), 1, 0),
       (27, 'password', '密码有效期(天)', 'PASSWORD_EXPIRATION_DAYS', '0', 1, NOW(), 1, 0),
       (28, 'password', '历史密码不可重复次数', 'PASSWORD_HISTORY_COUNT', '0', 1, NOW(), 1, 0);

-- 短信配置
-- 邮件配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_time, status, is_deleted)
VALUES (40, 'mail', 'SMTP 服务器', 'MAIL_HOST', '', 1, NOW(), 1, 0),
       (41, 'mail', 'SMTP 端口', 'MAIL_PORT', '465', 1, NOW(), 1, 0),
       (42, 'mail', '邮箱账号', 'MAIL_USERNAME', '', 1, NOW(), 1, 0),
       (43, 'mail', '邮箱密码/授权码', 'MAIL_PASSWORD', '', 1, NOW(), 1, 0),
       (44, 'mail', '发件地址', 'MAIL_FROM', '', 1, NOW(), 1, 0),
       (45, 'mail', '发件人名称', 'MAIL_FROM_NAME', '', 1, NOW(), 1, 0),
       (46, 'mail', '是否 SSL', 'MAIL_SSL_ENABLED', 'true', 1, NOW(), 1, 0);

-- 第三方登录配置（各平台需填写对应 clientId/clientSecret/redirectUri 后生效）
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_time, status, is_deleted)
VALUES (50, 'social', 'GitHub ClientId', 'SOCIAL_GITHUB_CLIENT_ID', '', 1, NOW(), 1, 0),
       (51, 'social', 'GitHub ClientSecret', 'SOCIAL_GITHUB_CLIENT_SECRET', '', 1, NOW(), 1, 0),
       (52, 'social', 'GitHub 回调地址', 'SOCIAL_GITHUB_REDIRECT_URI', '', 1, NOW(), 1, 0),
       (53, 'social', 'Gitee ClientId', 'SOCIAL_GITEE_CLIENT_ID', '', 1, NOW(), 1, 0),
       (54, 'social', 'Gitee ClientSecret', 'SOCIAL_GITEE_CLIENT_SECRET', '', 1, NOW(), 1, 0),
       (55, 'social', 'Gitee 回调地址', 'SOCIAL_GITEE_REDIRECT_URI', '', 1, NOW(), 1, 0),
       (56, 'social', 'QQ ClientId', 'SOCIAL_QQ_CLIENT_ID', '', 1, NOW(), 1, 0),
       (57, 'social', 'QQ ClientSecret', 'SOCIAL_QQ_CLIENT_SECRET', '', 1, NOW(), 1, 0),
       (58, 'social', 'QQ 回调地址', 'SOCIAL_QQ_REDIRECT_URI', '', 1, NOW(), 1, 0),
       (59, 'social', '微信开放平台 ClientId', 'SOCIAL_WECHAT_OPEN_CLIENT_ID', '', 1, NOW(), 1, 0),
       (60, 'social', '微信开放平台 ClientSecret', 'SOCIAL_WECHAT_OPEN_CLIENT_SECRET', '', 1, NOW(), 1, 0),
       (61, 'social', '微信开放平台 回调地址', 'SOCIAL_WECHAT_OPEN_REDIRECT_URI', '', 1, NOW(), 1, 0),
       (62, 'social', '支付宝 ClientId', 'SOCIAL_ALIPAY_CLIENT_ID', '', 1, NOW(), 1, 0),
       (63, 'social', '支付宝 ClientSecret', 'SOCIAL_ALIPAY_CLIENT_SECRET', '', 1, NOW(), 1, 0),
       (64, 'social', '支付宝 回调地址', 'SOCIAL_ALIPAY_REDIRECT_URI', '', 1, NOW(), 1, 0),
       (65, 'social', '钉钉 ClientId', 'SOCIAL_DINGTALK_CLIENT_ID', '', 1, NOW(), 1, 0),
       (66, 'social', '钉钉 ClientSecret', 'SOCIAL_DINGTALK_CLIENT_SECRET', '', 1, NOW(), 1, 0),
       (67, 'social', '钉钉 回调地址', 'SOCIAL_DINGTALK_REDIRECT_URI', '', 1, NOW(), 1, 0);

INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_time, status, is_deleted)
VALUES (30, 'sms', '短信厂商', 'SMS_SUPPLIER', '', 1, NOW(), 1, 0),
       (31, 'sms', 'AccessKeyId', 'SMS_ACCESS_KEY_ID', '', 1, NOW(), 1, 0),
       (32, 'sms', 'AccessKeySecret', 'SMS_ACCESS_KEY_SECRET', '', 1, NOW(), 1, 0),
       (33, 'sms', '短信签名', 'SMS_SIGNATURE', '', 1, NOW(), 1, 0),
       (34, 'sms', '验证码模板ID', 'SMS_TEMPLATE_ID', '', 1, NOW(), 1, 0),
       (35, 'sms', '验证码有效期(秒)', 'SMS_CODE_EXPIRE_SECONDS', '300', 1, NOW(), 1, 0);

-- 系统参数菜单 + 按钮权限
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (260, 2, 'SystemConfig', 'menu', '/system/config', '/system/config/index', 'system:config:list', 'system.config.title', 'carbon:settings-adjust', 6, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (26001, 260, 'SystemConfigAdd', 'button', 'system:config:add', 'common.create', 1, NOW(), 1, 0),
       (26002, 260, 'SystemConfigEdit', 'button', 'system:config:edit', 'common.edit', 2, NOW(), 1, 0),
       (26003, 260, 'SystemConfigDelete', 'button', 'system:config:delete', 'common.delete', 3, NOW(), 1, 0);
