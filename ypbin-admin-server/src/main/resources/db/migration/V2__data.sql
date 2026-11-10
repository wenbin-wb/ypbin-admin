-- =============================================================
-- ypbin-admin 种子数据（系统 + AI + 空间/安全，开发阶段整合版）
-- 默认租户 1；admin/approver 为平台用户，其他为租户用户
-- 固定 ID 便于关联，雪花 ID 从业务新增时生成
-- 注意：开发阶段保留可登录演示账号，上线前必须清理默认凭据
-- 说明：开发阶段整合为单文件（含演示用户/部门/岗位/空间）；boot-miniapp 的小程序种子在 V3__miniapp.sql。
-- =============================================================

-- 默认权限模板（全部权限）
INSERT INTO sys_auth_template (id, name, code, remark, create_user, create_time, status, is_deleted)
VALUES (1, '全部权限', 'ALL', '内置模板：拥有全部菜单权限', 1, NOW(), 1, 0);

-- 默认租户（绑定全部权限模板）
INSERT INTO sys_tenant (id, name, code, template_id, contact_name, remark, create_time, status, is_deleted)
VALUES (1, '默认租户', 'default', 1, 'admin', '系统内置默认租户', NOW(), 1, 0);

-- 部门树（测试数据）
INSERT INTO sys_dept (id, tenant_id, pid, name, sort, leader, phone, email, remark, create_user, create_time, status, is_deleted)
VALUES (1, 1, 0, '长沙词云信息科技', 1, '张伟', '0731-88888888', 'hr@ciyun.com', '总公司', 1, NOW(), 1, 0),
       (2, 1, 1, '研发中心', 1, '李强', '13800000001', 'rd@ciyun.com', NULL, 1, NOW(), 1, 0),
       (3, 1, 1, '产品中心', 2, '王芳', '13800000002', 'product@ciyun.com', NULL, 1, NOW(), 1, 0),
       (4, 1, 1, '市场部', 3, '赵敏', '13800000003', 'market@ciyun.com', NULL, 1, NOW(), 1, 0),
       (5, 1, 1, '职能中心', 4, '陈静', '13800000004', 'admin@ciyun.com', NULL, 1, NOW(), 1, 0),
       (6, 1, 2, '后端组', 1, '刘洋', NULL, NULL, NULL, 1, NOW(), 1, 0),
       (7, 1, 2, '前端组', 2, '孙磊', NULL, NULL, NULL, 1, NOW(), 1, 0),
       (8, 1, 2, '测试组', 3, '周涛', NULL, NULL, NULL, 1, NOW(), 1, 0),
       (9, 1, 5, '人事部', 1, '陈静', NULL, NULL, NULL, 1, NOW(), 1, 0),
       (10, 1, 5, '财务部', 2, '杨丽', NULL, NULL, NULL, 1, NOW(), 1, 0);

-- 用户（平台用户：admin/approver 密码统一 pt5aQ5E6t8dkVkMp（与前端演示登录默认一致）；
--      租户测试用户密码统一 123456）
-- pwd_change_required=1：平台账号首次登录必须先改密（拦截器只放行改密/登出）；
-- install.sh 部署时还会用 SUPER_ADMIN_INIT_PASSWORD 覆盖 admin 的这条演示口令。
INSERT INTO sys_user (id, tenant_id, username, user_type, password, real_name, nickname, dept_id, phone, email, gender,
                      remark, pwd_reset_time, pwd_change_required, create_user, create_time, status, is_deleted)
VALUES (1, 1, 'admin', 'PLATFORM', '$2a$10$4Yo3GSCsw0TwHl9xYrvPyO0iEu97Fov2uX8hHz3Z1Izfb6ETFKyFO',
        '超级管理员', '超级管理员', 1, NULL, NULL, 1, '系统内置超级管理员', NOW(), 1, NULL, NOW(), 1, 0),
       (6, 1, 'approver', 'PLATFORM', '$2a$10$4Yo3GSCsw0TwHl9xYrvPyO0iEu97Fov2uX8hHz3Z1Izfb6ETFKyFO',
        '授权审批员', '审批员', 1, NULL, NULL, 1, '授权双人复核的第二审批人', NOW(), 1, 1, NOW(), 1, 0),
       (2, 1, 'lilei', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW',
        '李强', '强哥', 2, '13900000001', 'liqiang@ciyun.com', 1, '研发中心负责人', NOW(), 0, 1, NOW(), 1, 0),
       (3, 1, 'wangfang', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW',
        '王芳', '芳芳', 3, '13900000002', 'wangfang@ciyun.com', 2, '产品中心负责人', NOW(), 0, 1, NOW(), 1, 0),
       (4, 1, 'liuyang', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW',
        '刘洋', '洋洋', 6, '13900000003', 'liuyang@ciyun.com', 1, '后端组开发', NOW(), 0, 1, NOW(), 1, 0),
       (5, 1, 'zhoutao', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW',
        '周涛', '涛哥', 8, '13900000004', 'zhoutao@ciyun.com', 1, '测试组', NOW(), 0, 1, NOW(), 0, 0);

-- 角色（平台超级管理员 super，其余为租户角色）
INSERT INTO sys_role (id, tenant_id, name, code, role_type, data_scope, sort, remark, create_user, create_time, status, is_deleted)
VALUES (1, 1, '超级管理员', 'super', 'PLATFORM_SUPER', 1, 1, '系统内置超级管理员角色，拥有全部权限', NULL, NOW(), 1, 0),
       (2, 1, '管理员', 'admin', 'TENANT_ROLE', 1, 2, '拥有大部分管理权限', 1, NOW(), 1, 0),
       (3, 1, '普通员工', 'staff', 'TENANT_ROLE', 4, 3, '仅本人数据权限', 1, NOW(), 1, 0);

-- 用户-角色
INSERT INTO sys_user_role (user_id, role_id)
VALUES (1, 1), (6, 1), (2, 2), (3, 2), (4, 3), (5, 3);

-- 内置字典
INSERT INTO sys_dict (id, name, code, remark, create_user, create_time, status, is_deleted)
VALUES (1, '系统状态', 'sys_status', '通用启用/禁用状态', 1, NOW(), 1, 0),
       (2, '性别', 'sys_gender', '用户性别', 1, NOW(), 1, 0);

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
VALUES (11, 1, '正常', '1', 'success', 1, 1, NOW(), 1, 0),
       (12, 1, '禁用', '0', 'error', 2, 1, NOW(), 1, 0),
       (21, 2, '未知', '0', 'default', 1, 1, NOW(), 1, 0),
       (22, 2, '男', '1', 'processing', 2, 1, NOW(), 1, 0),
       (23, 2, '女', '2', 'warning', 3, 1, NOW(), 1, 0);

-- 默认 Web 管理后台客户端
INSERT INTO sys_client (id, client_id, client_type, auth_types, timeout, active_timeout, concurrent_enabled, create_user, create_time, status, is_deleted)
VALUES (1, 'web-admin', 'WEB', 'ACCOUNT,PHONE,EMAIL', 86400, 1800, 1, 1, NOW(), 1, 0);

-- 岗位
INSERT INTO sys_post (id, tenant_id, name, code, category, sort, remark, create_user, create_time, status, is_deleted)
VALUES (11, 1, '总经理', 'CEO', '管理', 1, NULL, 1, NOW(), 1, 0),
       (12, 1, '技术总监', 'CTO', '管理', 2, NULL, 1, NOW(), 1, 0),
       (13, 1, '研发工程师', 'DEV', '技术', 3, NULL, 1, NOW(), 1, 0),
       (14, 1, '产品经理', 'PM', '产品', 4, NULL, 1, NOW(), 1, 0),
       (15, 1, '测试工程师', 'QA', '技术', 5, NULL, 1, NOW(), 1, 0),
       (16, 1, '人事专员', 'HR', '职能', 6, NULL, 1, NOW(), 1, 0);

-- 用户-岗位
INSERT INTO sys_user_post (user_id, post_id)
VALUES (2, 12), (3, 14), (4, 13), (5, 15);

-- 示例公告（不同状态/类型/范围）
INSERT INTO sys_notice (id, tenant_id, title, content, notice_type, notice_scope, scope_target_ids, notify_methods, is_top, publish_type, publish_status, publish_version, publish_time, create_user, create_time, status, is_deleted)
VALUES (1, 1, '系统上线通知', '<p>词云信息科技管理后台正式上线，欢迎大家使用。</p>', 1, 1, NULL, 'site', 1, 1, 2, 0, NOW(), 1, NOW(), 1, 0),
       (2, 1, '五一放假安排', '<p>5 月 1 日至 5 月 5 日放假，共 5 天。</p>', 2, 1, NULL, 'site,email', 0, 1, 2, 0, NOW(), 1, NOW(), 1, 0),
       (3, 1, '研发中心周会通知', '<p>本周五下午 3 点研发中心全员周会。</p>', 1, 3, '2', 'site', 0, 1, 2, 0, NOW(), 1, NOW(), 1, 0),
       (4, 1, '年终总结大会（草稿）', '<p>年终总结大会筹备中……</p>', 2, 1, NULL, 'site', 0, 2, 0, 0, NULL, 1, NOW(), 1, 0);

-- =============================================================
-- 系统参数
-- =============================================================
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (1, 'site', '系统名称', 'SITE_NAME', 'ypbin-admin', 1, 1, NOW(), 1, 0),
       (2, 'site', '版权信息', 'SITE_COPYRIGHT', 'ypbin', 1, 1, NOW(), 1, 0),
       (10, 'login', '是否开启登录验证码', 'LOGIN_CAPTCHA_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (11, 'login', '是否开启短信验证码登录', 'LOGIN_SMS_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (12, 'login', '是否开启用户自助注册', 'REGISTER_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (13, 'login', '注册用户默认租户 ID', 'REGISTER_DEFAULT_TENANT_ID', '1', 1, 1, NOW(), 1, 0),
       -- 默认角色留空：必须由管理员显式指定一个已分配菜单权限的租户角色后再开启注册，
       -- 避免「注册成功但没有任何菜单」的静默空系统（内置角色在种子数据里没有菜单授权）
       (14, 'login', '注册用户默认角色标识', 'REGISTER_DEFAULT_ROLE_CODE', '', 1, 1, NOW(), 1, 0),
       (20, 'password', '密码最小长度', 'PASSWORD_MIN_LENGTH', '8', 1, 1, NOW(), 1, 0),
       (21, 'password', '是否必须含数字', 'PASSWORD_REQUIRE_DIGIT', 'true', 1, 1, NOW(), 1, 0),
       (22, 'password', '是否必须含字母', 'PASSWORD_REQUIRE_LETTER', 'true', 1, 1, NOW(), 1, 0),
       (23, 'password', '是否必须含特殊字符', 'PASSWORD_REQUIRE_SYMBOL', 'false', 1, 1, NOW(), 1, 0),
       (24, 'password', '是否允许含用户名', 'PASSWORD_ALLOW_CONTAIN_USERNAME', 'false', 1, 1, NOW(), 1, 0),
       (25, 'password', '登录错误锁定阈值', 'PASSWORD_ERROR_LOCK_COUNT', '5', 1, 1, NOW(), 1, 0),
       (26, 'password', '账号锁定时长(分钟)', 'PASSWORD_LOCK_MINUTES', '15', 1, 1, NOW(), 1, 0),
       (27, 'password', '密码有效期(天)', 'PASSWORD_EXPIRATION_DAYS', '0', 1, 1, NOW(), 1, 0),
       (28, 'password', '历史密码不可重复次数', 'PASSWORD_HISTORY_COUNT', '0', 1, 1, NOW(), 1, 0),
       (30, 'sms', '短信厂商', 'SMS_SUPPLIER', '', 1, 1, NOW(), 1, 0),
       (31, 'sms', 'AccessKeyId', 'SMS_ACCESS_KEY_ID', '', 1, 1, NOW(), 1, 0),
       (32, 'sms', 'AccessKeySecret', 'SMS_ACCESS_KEY_SECRET', '', 1, 1, NOW(), 1, 0),
       (33, 'sms', '短信签名', 'SMS_SIGNATURE', '', 1, 1, NOW(), 1, 0),
       (34, 'sms', '验证码模板ID', 'SMS_TEMPLATE_ID', '', 1, 1, NOW(), 1, 0),
       (35, 'sms', '验证码有效期(秒)', 'SMS_CODE_EXPIRE_SECONDS', '300', 1, 1, NOW(), 1, 0),
       (36, 'sms', '验证码发送冷却(秒)', 'SMS_CODE_COOLDOWN_SECONDS', '60', 1, 1, NOW(), 1, 0),
       (40, 'mail', 'SMTP 服务器', 'MAIL_HOST', '', 1, 1, NOW(), 1, 0),
       (41, 'mail', 'SMTP 端口', 'MAIL_PORT', '465', 1, 1, NOW(), 1, 0),
       (42, 'mail', '邮箱账号', 'MAIL_USERNAME', '', 1, 1, NOW(), 1, 0),
       (43, 'mail', '邮箱密码/授权码', 'MAIL_PASSWORD', '', 1, 1, NOW(), 1, 0),
       (44, 'mail', '发件地址', 'MAIL_FROM', '', 1, 1, NOW(), 1, 0),
       (45, 'mail', '发件人名称', 'MAIL_FROM_NAME', '', 1, 1, NOW(), 1, 0),
       (46, 'mail', '是否 SSL', 'MAIL_SSL_ENABLED', 'true', 1, 1, NOW(), 1, 0),
       (50, 'social', 'GitHub ClientId', 'SOCIAL_GITHUB_CLIENT_ID', '', 1, 1, NOW(), 1, 0),
       (51, 'social', 'GitHub ClientSecret', 'SOCIAL_GITHUB_CLIENT_SECRET', '', 1, 1, NOW(), 1, 0),
       (52, 'social', 'GitHub 回调地址', 'SOCIAL_GITHUB_REDIRECT_URI', '', 1, 1, NOW(), 1, 0),
       (53, 'social', 'Gitee ClientId', 'SOCIAL_GITEE_CLIENT_ID', '', 1, 1, NOW(), 1, 0),
       (54, 'social', 'Gitee ClientSecret', 'SOCIAL_GITEE_CLIENT_SECRET', '', 1, 1, NOW(), 1, 0),
       (55, 'social', 'Gitee 回调地址', 'SOCIAL_GITEE_REDIRECT_URI', '', 1, 1, NOW(), 1, 0),
       (56, 'social', 'QQ ClientId', 'SOCIAL_QQ_CLIENT_ID', '', 1, 1, NOW(), 1, 0),
       (57, 'social', 'QQ ClientSecret', 'SOCIAL_QQ_CLIENT_SECRET', '', 1, 1, NOW(), 1, 0),
       (58, 'social', 'QQ 回调地址', 'SOCIAL_QQ_REDIRECT_URI', '', 1, 1, NOW(), 1, 0),
       (59, 'social', '微信开放平台 ClientId', 'SOCIAL_WECHAT_OPEN_CLIENT_ID', '', 1, 1, NOW(), 1, 0),
       (60, 'social', '微信开放平台 ClientSecret', 'SOCIAL_WECHAT_OPEN_CLIENT_SECRET', '', 1, 1, NOW(), 1, 0),
       (61, 'social', '微信开放平台 回调地址', 'SOCIAL_WECHAT_OPEN_REDIRECT_URI', '', 1, 1, NOW(), 1, 0),
       (62, 'social', '支付宝 ClientId', 'SOCIAL_ALIPAY_CLIENT_ID', '', 1, 1, NOW(), 1, 0),
       (63, 'social', '支付宝 ClientSecret', 'SOCIAL_ALIPAY_CLIENT_SECRET', '', 1, 1, NOW(), 1, 0),
       (64, 'social', '支付宝 回调地址', 'SOCIAL_ALIPAY_REDIRECT_URI', '', 1, 1, NOW(), 1, 0),
       (65, 'social', '钉钉 ClientId', 'SOCIAL_DINGTALK_CLIENT_ID', '', 1, 1, NOW(), 1, 0),
       (66, 'social', '钉钉 ClientSecret', 'SOCIAL_DINGTALK_CLIENT_SECRET', '', 1, 1, NOW(), 1, 0),
       (67, 'social', '钉钉 回调地址', 'SOCIAL_DINGTALK_REDIRECT_URI', '', 1, 1, NOW(), 1, 0),
       (68, 'social', 'GitHub 是否启用', 'SOCIAL_GITHUB_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (69, 'social', 'Gitee 是否启用', 'SOCIAL_GITEE_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (70, 'social', 'QQ 是否启用', 'SOCIAL_QQ_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (71, 'social', '微信开放平台是否启用', 'SOCIAL_WECHAT_OPEN_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (72, 'social', '支付宝是否启用', 'SOCIAL_ALIPAY_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (73, 'social', '钉钉是否启用', 'SOCIAL_DINGTALK_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (74, 'social', '支付宝公钥', 'SOCIAL_ALIPAY_PUBLIC_KEY', '', 1, 1, NOW(), 1, 0);

-- =============================================================
-- 菜单树（platform_only=1 为平台专用菜单；0 为租户可见）
-- =============================================================

-- 仪表盘
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (1, 0, 'Dashboard', 'catalog', 1, '/dashboard', 'BasicLayout', 'page.dashboard.title', 'lucide:layout-dashboard', -1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, keep_alive, sort, create_time, status, is_deleted)
VALUES (101, 1, 'Analytics', 'menu', 1, '/dashboard/analytics', '/dashboard/analytics/index', 'page.dashboard.analytics', 'lucide:area-chart', 1, 1, NOW(), 1, 0),
       (102, 1, 'Workspace', 'menu', 1, '/dashboard/workspace', '/dashboard/workspace/index', 'page.dashboard.workspace', 'carbon:workspace', 0, 2, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (103, 1, 'SystemDashboardView', 'button', 1, 'system:dashboard:view', 'system.dashboard.view', 3, NOW(), 1, 0);

-- 顶级分类目录 + 一级单页菜单
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (3001, 0, 'OrgManage', 'catalog', 0, '/system/org', 'BasicLayout', 'system.org.title', 'carbon:tree-view-alt', 1, NOW(), 1, 0),
       (3002, 0, 'AuthManage', 'catalog', 0, '/system/auth', 'BasicLayout', 'system.auth.title', 'carbon:security', 2, NOW(), 1, 0),
       (3005, 0, 'TenantManage', 'catalog', 1, '/system/tenant', 'BasicLayout', 'system.tenant.title', 'carbon:building', 3, NOW(), 1, 0),
       (3007, 0, 'MessageManage', 'catalog', 0, '/message-center', 'BasicLayout', 'system.messageCenter.title', 'carbon:notification', 6, NOW(), 1, 0),
       (3004, 0, 'MonitorManage', 'catalog', 1, '/system/monitor', 'BasicLayout', 'system.monitor.title', 'carbon:activity', 7, NOW(), 1, 0),
       (3003, 0, 'SysManage', 'catalog', 1, '/system/sys', 'BasicLayout', 'system.sys.title', 'carbon:settings-adjust', 8, NOW(), 1, 0),
       (3008, 0, 'LicenseManage', 'catalog', 1, '/system/license-manage', 'BasicLayout', 'system.license.title', 'carbon:license', 9, NOW(), 1, 0);

-- 接口文档（内嵌 iframe 打开后端 springdoc 的 swagger-ui）
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, iframe_src, title, icon, sort, create_time, status, is_deleted)
VALUES (4001, 0, 'ApiDoc', 'embedded', 0, '/api-doc', 'IFrameView', '/swagger-ui/index.html', 'system.apiDoc.title', 'carbon:document-tasks', 10, NOW(), 1, 0);

-- 文件管理（一级单页）
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2600, 0, 'SystemFile', 'menu', 1, '/system/file', '/system/file/list', 'system:file:list', 'system.file.title', 'carbon:document-attachment', 5, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (260001, 2600, 'SystemFileUpload', 'button', 1, 'system:file:upload', 'common.upload', 1, NOW(), 1, 0),
       (260002, 2600, 'SystemFileDelete', 'button', 1, 'system:file:delete', 'common.delete', 2, NOW(), 1, 0);

-- 消息中心（3007，共享目录）：通知公告（推送测试为平台专用）、我的消息
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2700, 3007, 'SystemNotice', 'menu', 0, '/system/notice', '/system/notice/list', 'system:notice:list', 'system.notice.title', 'carbon:notification', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (270001, 2700, 'SystemNoticeAdd', 'button', 0, 'system:notice:add', 'common.create', 1, NOW(), 1, 0),
       (270002, 2700, 'SystemNoticeEdit', 'button', 0, 'system:notice:edit', 'common.edit', 2, NOW(), 1, 0),
       (270003, 2700, 'SystemNoticeDelete', 'button', 0, 'system:notice:delete', 'common.delete', 3, NOW(), 1, 0),
       (270004, 2700, 'SystemPushTest', 'button', 1, 'system:push:test', 'system.push.test', 4, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (4000, 3007, 'MyMessage', 'menu', 0, '/message', '/_core/message/list', 'system.message.title', 'carbon:email', 2, NOW(), 1, 0);

-- 组织管理（3001）：用户 / 部门 / 岗位（租户可见）
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (210, 3001, 'SystemUser', 'menu', 0, '/system/user', '/system/user/list', 'system:user:list', 'system.user.title', 'carbon:user', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (21001, 210, 'SystemUserAdd', 'button', 0, 'system:user:add', 'common.create', 1, NOW(), 1, 0),
       (21002, 210, 'SystemUserEdit', 'button', 0, 'system:user:edit', 'common.edit', 2, NOW(), 1, 0),
       (21003, 210, 'SystemUserDelete', 'button', 0, 'system:user:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (240, 3001, 'SystemDept', 'menu', 0, '/system/dept', '/system/dept/list', 'system:dept:list', 'system.dept.title', 'carbon:container-services', 4, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (24001, 240, 'SystemDeptAdd', 'button', 0, 'system:dept:add', 'common.create', 1, NOW(), 1, 0),
       (24002, 240, 'SystemDeptEdit', 'button', 0, 'system:dept:edit', 'common.edit', 2, NOW(), 1, 0),
       (24003, 240, 'SystemDeptDelete', 'button', 0, 'system:dept:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2500, 3001, 'SystemPost', 'menu', 0, '/system/post', '/system/post/list', 'system:post:list', 'system.post.title', 'carbon:id-management', 10, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (250001, 2500, 'SystemPostAdd', 'button', 0, 'system:post:add', 'common.create', 1, NOW(), 1, 0),
       (250002, 2500, 'SystemPostEdit', 'button', 0, 'system:post:edit', 'common.edit', 2, NOW(), 1, 0),
       (250003, 2500, 'SystemPostDelete', 'button', 0, 'system:post:delete', 'common.delete', 3, NOW(), 1, 0);

-- 权限管理（3002，共享目录）：角色（租户可见）/ 菜单、客户端（平台专用）
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (220, 3002, 'SystemRole', 'menu', 0, '/system/role', '/system/role/list', 'system:role:list', 'system.role.title', 'carbon:user-role', 2, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (22001, 220, 'SystemRoleAdd', 'button', 0, 'system:role:add', 'common.create', 1, NOW(), 1, 0),
       (22002, 220, 'SystemRoleEdit', 'button', 0, 'system:role:edit', 'common.edit', 2, NOW(), 1, 0),
       (22003, 220, 'SystemRoleDelete', 'button', 0, 'system:role:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (230, 3002, 'SystemMenu', 'menu', 1, '/system/menu', '/system/menu/list', 'system:menu:list', 'system.menu.title', 'carbon:menu', 3, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (23001, 230, 'SystemMenuAdd', 'button', 1, 'system:menu:add', 'common.create', 1, NOW(), 1, 0),
       (23002, 230, 'SystemMenuEdit', 'button', 1, 'system:menu:edit', 'common.edit', 2, NOW(), 1, 0),
       (23003, 230, 'SystemMenuDelete', 'button', 1, 'system:menu:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (290, 3002, 'SystemClient', 'menu', 1, '/system/client', '/system/client/list', 'system:client:list', 'system.client.title', 'carbon:application', 9, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (29001, 290, 'SystemClientAdd', 'button', 1, 'system:client:add', 'common.create', 1, NOW(), 1, 0),
       (29002, 290, 'SystemClientEdit', 'button', 1, 'system:client:edit', 'common.edit', 2, NOW(), 1, 0),
       (29003, 290, 'SystemClientDelete', 'button', 1, 'system:client:delete', 'common.delete', 3, NOW(), 1, 0),
       (29004, 290, 'SystemClientResetSecret', 'button', 1, 'system:client:reset-secret', 'system.common.resetSecret', 4, NOW(), 1, 0);

-- 系统管理（3003，平台）：字典 / 参数
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (250, 3003, 'SystemDict', 'menu', 1, '/system/dict', '/system/dict/list', 'system:dict:list', 'system.dict.title', 'carbon:catalog', 5, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (25001, 250, 'SystemDictAdd', 'button', 1, 'system:dict:add', 'common.create', 1, NOW(), 1, 0),
       (25002, 250, 'SystemDictEdit', 'button', 1, 'system:dict:edit', 'common.edit', 2, NOW(), 1, 0),
       (25003, 250, 'SystemDictDelete', 'button', 1, 'system:dict:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (260, 3003, 'SystemConfig', 'menu', 1, '/system/config', '/system/config/index', 'system:config:list', 'system.config.title', 'carbon:settings-adjust', 6, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (26001, 260, 'SystemConfigAdd', 'button', 1, 'system:config:add', 'common.create', 1, NOW(), 1, 0),
       (26002, 260, 'SystemConfigEdit', 'button', 1, 'system:config:edit', 'common.edit', 2, NOW(), 1, 0),
       (26003, 260, 'SystemConfigDelete', 'button', 1, 'system:config:delete', 'common.delete', 3, NOW(), 1, 0),
       (26004, 260, 'SystemMailTest', 'button', 1, 'system:mail:test', 'system.mail.test', 4, NOW(), 1, 0);

-- 监控管理（3004，平台）：日志 / 在线用户
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (270, 3004, 'SystemLog', 'menu', 1, '/system/log', '/system/log/list', 'system:log:list', 'system.log.title', 'carbon:document', 7, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (280, 3004, 'SystemOnlineUser', 'menu', 1, '/system/online-user', '/system/online-user/list', 'system:online-user:list', 'system.onlineUser.title', 'carbon:user-online', 8, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (28001, 280, 'SystemOnlineUserKickout', 'button', 1, 'system:online-user:kickout', 'common.kickout', 1, NOW(), 1, 0);

-- 租户管理（3005，平台）：租户列表 / 权限模板
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2950, 3005, 'SystemTenant', 'menu', 1, '/system/tenant', '/system/tenant/list', 'system:tenant:list', 'system.tenant.list', 'carbon:building-insights-2', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (295001, 2950, 'SystemTenantAdd', 'button', 1, 'system:tenant:add', 'common.create', 1, NOW(), 1, 0),
       (295002, 2950, 'SystemTenantEdit', 'button', 1, 'system:tenant:edit', 'common.edit', 2, NOW(), 1, 0),
       (295003, 2950, 'SystemTenantDelete', 'button', 1, 'system:tenant:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2952, 3005, 'SystemAuthTemplate', 'menu', 1, '/system/auth-template', '/system/auth-template/list', 'system:auth-template:list', 'system.authTemplate.title', 'carbon:template', 2, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (295201, 2952, 'SystemAuthTemplateAdd', 'button', 1, 'system:auth-template:add', 'common.create', 1, NOW(), 1, 0),
       (295202, 2952, 'SystemAuthTemplateEdit', 'button', 1, 'system:auth-template:edit', 'common.edit', 2, NOW(), 1, 0),
       (295203, 2952, 'SystemAuthTemplateDelete', 'button', 1, 'system:auth-template:delete', 'common.delete', 3, NOW(), 1, 0);

-- 授权管理（3008，平台）：授权列表 + 开放应用
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (3100, 3008, 'SystemLicense', 'menu', 1, '/system/license', '/system/license/list', 'system:license:list', 'system.license.list', 'carbon:license', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (310001, 3100, 'SystemLicenseAdd', 'button', 1, 'system:license:add', 'common.create', 1, NOW(), 1, 0),
       (310002, 3100, 'SystemLicenseEdit', 'button', 1, 'system:license:edit', 'common.edit', 2, NOW(), 1, 0),
       (310003, 3100, 'SystemLicenseDelete', 'button', 1, 'system:license:delete', 'common.delete', 3, NOW(), 1, 0),
       (310004, 3100, 'SystemLicenseSubmit', 'button', 1, 'system:license:submit', 'system.license.submit', 4, NOW(), 1, 0),
       (310005, 3100, 'SystemLicenseApprove', 'button', 1, 'system:license:approve', 'system.license.approve', 5, NOW(), 1, 0),
       (310006, 3100, 'SystemLicenseRevoke', 'button', 1, 'system:license:revoke', 'system.license.revoke', 6, NOW(), 1, 0),
       (310007, 3100, 'SystemLicenseGenKey', 'button', 1, 'system:license:genkey', 'system.license.genkey', 7, NOW(), 1, 0),
       (310008, 3100, 'SystemLicenseDelivery', 'button', 1, 'system:license:delivery', 'system.license.deliveryTitle', 8, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2900, 3008, 'SystemApp', 'menu', 1, '/system/app', '/system/app/list', 'system:app:list', 'system.app.title', 'carbon:api', 2, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (290001, 2900, 'SystemAppAdd', 'button', 1, 'system:app:add', 'common.create', 1, NOW(), 1, 0),
       (290002, 2900, 'SystemAppEdit', 'button', 1, 'system:app:edit', 'common.edit', 2, NOW(), 1, 0),
       (290003, 2900, 'SystemAppDelete', 'button', 1, 'system:app:delete', 'common.delete', 3, NOW(), 1, 0),
       (290004, 2900, 'SystemAppResetSecret', 'button', 1, 'system:app:reset-secret', 'system.common.resetSecret', 4, NOW(), 1, 0);

-- 演示联机校验开放应用（供 ypbin-license-demo 零配置联机回验）
INSERT INTO sys_app (id, access_key, secret_key, app_name, enabled, create_user, create_time, status, is_deleted)
VALUES (1, '0a1b2c3d4e5f60718293a4b5c6d7e8f9', '98a7b6c5d4e3f2019a8b7c6d5e4f3021', 'ypbin-license-demo 联机校验', 1, 1, NOW(), 1, 0);

-- =============================================================
-- AI 菜单树（5000-5099 段；platform_only=1 为平台专用）
-- 说明：菜单与系统菜单统一在此管理，下方"菜单授权"全量授权自动覆盖
-- =============================================================

-- AI 顶级目录
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (5000, 0, 'AiManage', 'catalog', 0, '/ai', 'BasicLayout', 'page.ai.title', 'carbon:machine-learning', 9, NOW(), 1, 0);

-- AI 一级菜单
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES
(5001, 5000, 'AiChat',      'menu', 0, '/ai/chat',      '/ai/chat/index',      'page.ai.chat.title',      'carbon:chat-bot',          1, NOW(), 1, 0),
(5002, 5000, 'AiKnowledge', 'menu', 0, '/ai/knowledge', '/ai/knowledge/index', 'page.ai.knowledge.title', 'carbon:data-base',          2, NOW(), 1, 0),
(5003, 5000, 'AiConfig',    'menu', 1, '/ai/config',    '/ai/config/index',    'page.ai.config.title',    'carbon:settings-services',  3, NOW(), 1, 0),
(5040, 5000, 'AiPrompt',    'menu', 0, '/ai/prompt',    '/ai/prompt/index',    'page.ai.prompt.title',    'carbon:template',           4, NOW(), 1, 0),
(5050, 5000, 'AiUsage',     'menu', 1, '/ai/usage',     '/ai/usage/index',     'page.ai.usage.title',     'carbon:analytics',          5, NOW(), 1, 0),
(5007, 5000, 'AiWiki',      'menu', 0, '/ai/wiki',      '/ai/wiki/index',      'page.ai.wiki.title',      'carbon:document',           6, NOW(), 1, 0),
(5060, 5000, 'AiRole',      'menu', 0, '/ai/role',      '/ai/role/index',      'page.ai.role.title',      'carbon:user-role',          7, NOW(), 1, 0);

-- AI 权限按钮
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES
(5011, 5001, 'AiChatSend',         'button', 0, 'ai:chat:send',           'page.ai.chat.send',          1, NOW(), 1, 0),
(5012, 5001, 'AiChatList',         'button', 0, 'ai:chat:list',           'page.ai.chat.title',         2, NOW(), 1, 0),
(5013, 5001, 'AiChatCreate',       'button', 0, 'ai:chat:create',         'page.ai.chat.title',         3, NOW(), 1, 0),
(5014, 5001, 'AiChatDelete',       'button', 0, 'ai:chat:delete',         'page.ai.chat.title',         4, NOW(), 1, 0),
(5015, 5001, 'AiChatEdit',         'button', 0, 'ai:chat:edit',           'page.ai.chat.title',         5, NOW(), 1, 0),
(5021, 5002, 'AiKnowledgeList',    'button', 0, 'ai:knowledge:list',      'page.ai.knowledge.list',     1, NOW(), 1, 0),
(5022, 5002, 'AiKnowledgeCreate',  'button', 0, 'ai:knowledge:create',    'page.ai.knowledge.create',   2, NOW(), 1, 0),
(5023, 5002, 'AiKnowledgeDelete',  'button', 0, 'ai:knowledge:delete',    'page.ai.knowledge.delete',   3, NOW(), 1, 0),
(5024, 5002, 'AiDocumentUpload',   'button', 0, 'ai:document:upload',     'page.ai.document.upload',    4, NOW(), 1, 0),
(5025, 5002, 'AiDocumentDelete',   'button', 0, 'ai:document:delete',     'page.ai.document.delete',    5, NOW(), 1, 0),
(5031, 5003, 'AiModelList',        'button', 1, 'ai:model:list',          'page.ai.model.list',         1, NOW(), 1, 0),
(5032, 5003, 'AiModelCreate',      'button', 1, 'ai:model:create',        'page.ai.model.create',       2, NOW(), 1, 0),
(5033, 5003, 'AiModelEdit',        'button', 1, 'ai:model:edit',          'page.ai.model.edit',         3, NOW(), 1, 0),
(5034, 5003, 'AiModelDelete',      'button', 1, 'ai:model:delete',        'page.ai.model.delete',       4, NOW(), 1, 0),
(5041, 5040, 'AiPromptList',       'button', 0, 'ai:prompt:list',         'page.ai.prompt.list',        1, NOW(), 1, 0),
(5042, 5040, 'AiPromptCreate',     'button', 0, 'ai:prompt:create',       'page.ai.prompt.create',      2, NOW(), 1, 0),
(5043, 5040, 'AiPromptEdit',       'button', 0, 'ai:prompt:edit',         'page.ai.prompt.edit',        3, NOW(), 1, 0),
(5044, 5040, 'AiPromptDelete',     'button', 0, 'ai:prompt:delete',       'page.ai.prompt.delete',      4, NOW(), 1, 0),
(5051, 5050, 'AiUsageView',        'button', 1, 'ai:usage:view',          'page.ai.usage.view',         1, NOW(), 1, 0),
(5071, 5007, 'AiWikiView',         'button', 0, 'ai:knowledge:list',      'page.ai.wiki.view',          1, NOW(), 1, 0),
(5061, 5060, 'AiRoleList',         'button', 0, 'ai:role:list',           'page.ai.role.title',         1, NOW(), 1, 0),
(5062, 5060, 'AiRoleCreate',       'button', 0, 'ai:role:create',         'page.ai.role.title',         2, NOW(), 1, 0),
(5063, 5060, 'AiRoleEdit',         'button', 0, 'ai:role:edit',           'page.ai.role.title',         3, NOW(), 1, 0),
(5064, 5060, 'AiRoleDelete',       'button', 0, 'ai:role:delete',         'page.ai.role.title',         4, NOW(), 1, 0);

-- =============================================================
-- 菜单授权
-- =============================================================
-- 全部权限模板只授权租户可见菜单
INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, id FROM sys_menu WHERE is_deleted = 0 AND platform_only = 0;

-- 平台超级管理员获得全部平台专用菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE is_deleted = 0 AND platform_only = 1;

-- 一次性初始化：平台管理员 bootstrap 预留 PENDING 记录
INSERT INTO sys_bootstrap_state (bootstrap_key, state, create_time, update_time)
VALUES ('platform-admin', 'PENDING', NOW(), NOW());
-- =============================================================
-- ypbin-admin AI 种子数据
-- 内置对话角色（tenant_id=0 为全局内置角色）
-- AI 菜单与权限种子见本文件系统菜单段（随全量授权自动覆盖）
-- =============================================================

-- 内置对话角色（5 个经典角色）
INSERT INTO ai_chat_role (id, tenant_id, name, description, avatar, system_prompt, category, temperature, is_builtin, sort, create_user, status, is_deleted)
VALUES
(1, 0, '通用助手', '全能 AI 助手，可回答各类问题、协助工作', NULL,
 '你是一个有帮助、无害、诚实的 AI 助手。请用简洁、专业的语言回答用户的问题。',
 'assistant', 0.70, 1, 1, 1, 1, 0),

(2, 0, '翻译专家', '精通中英互译，支持多语言翻译', NULL,
 '你是一位专业翻译，擅长中英文互译。请保持原文的语气、风格和专业术语的准确性。翻译时：1) 准确传达原意 2) 符合目标语言习惯 3) 保留专业术语。直接输出翻译结果，无需解释。',
 'translator', 0.30, 1, 2, 1, 1, 0),

(3, 0, '代码助手', '编程专家，提供代码编写、调试、优化建议', NULL,
 '你是一位资深软件工程师，精通多种编程语言和框架。回答编程问题时：1) 提供可运行的代码示例 2) 解释关键逻辑 3) 给出最佳实践建议 4) 标注潜在问题。代码用 Markdown 代码块格式化。',
 'coder', 0.20, 1, 3, 1, 1, 0),

(4, 0, '数据分析师', '擅长数据解读、图表分析、业务洞察', NULL,
 '你是一位数据分析专家，擅长从数据中提取洞察。分析时：1) 识别关键指标和趋势 2) 给出数据驱动的结论 3) 提供可执行的建议 4) 用简洁的语言解释复杂概念。支持 SQL、Python、Excel 等工具。',
 'analyst', 0.50, 1, 4, 1, 1, 0),

(5, 0, '创意写作', '文案创作、故事生成、内容润色', NULL,
 '你是一位富有创意的作家，擅长各类文体创作。写作时：1) 语言生动、富有感染力 2) 结构清晰、逻辑连贯 3) 根据用户需求调整风格（正式/轻松/幽默）4) 必要时提供多个版本供选择。',
 'writer', 0.90, 1, 5, 1, 1, 0);


-- =============================================================
-- 种子合并：安全防护菜单与授权（原 V6/V7 的数据部分）
-- =============================================================

-- 安全防护菜单（平台专用）：目录 + 黑名单页面 + 按钮权限
INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort,
                             create_time, status, is_deleted)
VALUES (3009, 0, 'SecurityManage', 'catalog', 1, '/system/security', 'BasicLayout',
        'system.blocklist.title', 'carbon:security-services', 7, NOW(), 1, 0);

INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon,
                             sort, create_time, status, is_deleted)
VALUES (3200, 3009, 'SystemAccessBlocklist', 'menu', 1, '/system/security/blocklist',
        '/system/security/blocklist/index', 'system:blocklist:list', 'system.blocklist.list',
        'carbon:firewall', 1, NOW(), 1, 0);

INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                             create_time, status, is_deleted)
VALUES (320001, 3200, 'SystemAccessBlocklistAdd', 'button', 1, 'system:blocklist:add',
        'common.create', 1, NOW(), 1, 0),
       (320002, 3200, 'SystemAccessBlocklistEdit', 'button', 1, 'system:blocklist:edit',
        'common.edit', 2, NOW(), 1, 0),
       (320003, 3200, 'SystemAccessBlocklistDelete', 'button', 1, 'system:blocklist:delete',
        'common.delete', 3, NOW(), 1, 0),
       (320004, 3200, 'SystemAccessBlocklistUnban', 'button', 1, 'system:blocklist:unban',
        'system.blocklist.unban', 4, NOW(), 1, 0);

-- 平台超级管理员获得新增菜单（V2 的全量授权只覆盖当时已存在的菜单）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 3009),
       (1, 3200),
       (1, 320001),
       (1, 320002),
       (1, 320003),
       (1, 320004);

-- =============================================================
-- 访问黑名单：临时封禁按钮权限（V7，2026-11-05）
-- =============================================================

INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                             create_time, status, is_deleted)
VALUES (320005, 3200, 'SystemAccessBlocklistBan', 'button', 1, 'system:blocklist:ban',
        'system.blocklist.ban', 5, NOW(), 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 320005);

-- =============================================================
-- 访问黑名单：常用规则种子
-- 默认启用的是「明显恶意/探测」类规则；与业务路径冲突时可在管理端停用。
-- =============================================================
INSERT IGNORE INTO sys_access_blocklist
    (id, match_type, match_value, reason, expire_time, create_user, create_time, status, is_deleted)
VALUES (7001, 'PATH', '/actuator/env', '探测 Spring Actuator 环境变量端点', NULL, 1, NOW(), 1, 0),
       (7002, 'PATH', '/actuator/heapdump', '探测 Spring Actuator 堆转储端点', NULL, 1, NOW(), 1, 0),
       (7003, 'PATH', '/actuator/configprops', '探测 Spring Actuator 配置属性端点', NULL, 1, NOW(), 1, 0),
       (7004, 'PATH', '/actuator/beans', '探测 Spring Actuator Bean 列表端点', NULL, 1, NOW(), 1, 0),
       (7005, 'PATH', '/.env', '探测 .env 环境变量文件', NULL, 1, NOW(), 1, 0),
       (7006, 'PATH', '/.git/', '探测 Git 仓库目录', NULL, 1, NOW(), 1, 0),
       (7007, 'PATH', '/wp-admin', '扫描 WordPress 后台', NULL, 1, NOW(), 1, 0),
       (7008, 'PATH', '/phpmyadmin', '扫描 phpMyAdmin', NULL, 1, NOW(), 1, 0),
       (7009, 'PATH', '/druid', '扫描 Druid 监控台', NULL, 1, NOW(), 1, 0),
       (7010, 'USER_AGENT', 'sqlmap', 'SQL 注入扫描器', NULL, 1, NOW(), 1, 0),
       (7011, 'USER_AGENT', 'nikto', 'Web 漏洞扫描器', NULL, 1, NOW(), 1, 0),
       (7012, 'USER_AGENT', 'nmap', '端口/服务扫描器', NULL, 1, NOW(), 1, 0),
       (7013, 'USER_AGENT', 'masscan', '高速端口扫描器', NULL, 1, NOW(), 1, 0),
       (7014, 'USER_AGENT', 'acunetix', 'Web 漏洞扫描器', NULL, 1, NOW(), 1, 0),
       (7015, 'USER_AGENT', 'gobuster', '目录爆破工具', NULL, 1, NOW(), 1, 0);


-- =============================================================
-- 种子合并：空间字典/菜单/授权/自检（原 V8 的数据部分）
-- =============================================================

-- ---------- 3) 字典：空间类型 / 关联对象类型 ----------
-- 用固定 ID（3 / 4）与 lib 内既有字典（1 系统状态、2 性别）连续，避免雪花 ID 让升级库与全新库不一致。
-- ⚠️ 必须按 **code** 判存在，不能按 id：平台管理员可能已自建过同名/同 id 的字典。
--     按 id 判会让 id 被别的字典占用时静默跳过 → dict_id=3 不存在、但下面的字典项照插 ⇒ 孤儿项（实测）。
-- 也**不用 INSERT IGNORE**：IGNORE 会把守卫之外的冲突（主键/唯一键）一并吞掉，掩盖真实失败。
INSERT INTO sys_dict (id, name, code, remark, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict),
       '空间类型', 'space_type', '楼栋/楼层/房间/办公室/仓库/宿舍等', 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE code = 'space_type');
INSERT INTO sys_dict (id, name, code, remark, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict),
       '空间关联对象类型', 'space_target_type', '部门/人员/设备/资产等可挂到空间上的业务对象',
       1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE code = 'space_target_type');

-- 字典项：按 (dict_id, value) 存在性守卫 —— 不用 IGNORE 是因为唯一键只在 code 上，
-- 重复插入 item 不会撞唯一键（没有该约束），只能显式判存在。
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '园区', 'park', 'processing', 1, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'park');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '楼栋', 'building', 'processing', 2, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'building');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '楼层', 'floor', 'processing', 3, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'floor');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '办公室', 'office', 'success', 4, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'office');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '仓库', 'warehouse', 'warning', 5, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'warehouse');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '宿舍', 'dormitory', 'default', 6, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'dormitory');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '会议室', 'meeting_room', 'default', 7, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'meeting_room');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '车间', 'workshop', 'default', 8, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'workshop');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '机房', 'server_room', 'error', 9, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'server_room');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '其他', 'other', 'default', 99, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'other');

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '部门', 'dept', 'processing', 1, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'dept');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '人员', 'user', 'processing', 2, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'user');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '设备', 'device', 'success', 3, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'device');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '资产', 'asset', 'default', 4, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'asset');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '其他', 'other', 'default', 99, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'other');

-- ---------- 4) 菜单与权限码 ----------
-- 空间管理挂在「组织管理」（3001）下：空间与用户/部门/岗位同属基础主数据，
-- 单开顶级目录会让它看起来像某个业务域的功能（IoT 的错觉正是要消除的）。
--
-- ⚠️ 判存在按 **auth_code**，不按 id：id 可能已被无关菜单占用（实测形态：id=2410 被占 ⇒
--    原写法静默跳过插入、却仍给 2410 授权 ⇒ 把 system:space:list 白授给了别人的菜单）。
-- 按钮的 pid 用 (SELECT id FROM sys_menu WHERE auth_code='...') 反查父页真实 id。
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu), 3001,
       'SystemSpace', 'menu', 0, '/system/space', '/system/space/list',
       'system:space:list', 'system.space.title', 'carbon:location', 5, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:list');

INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceAdd', 'button', 0, 'system:space:add', 'common.create', 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:add');
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceEdit', 'button', 0, 'system:space:edit', 'common.edit', 2, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:edit');
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceDelete', 'button', 0, 'system:space:delete', 'common.delete', 3, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:delete');
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceBind', 'button', 0, 'system:space:bind', 'system.space.bind', 4, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:bind');

-- ---------- 5) 授权 ----------
-- platform_only=0 的菜单不会被 002-data.sql 尾部那条「只授 platform_only=1」覆盖，必须显式双授：
--   sys_role_menu     → 平台管理员在菜单树里看得到
--   sys_template_menu → 租户可授菜单（新建/改角色时能被勾选）
-- 缺任一张的表现不同：缺 role 表是管理员看不到，缺 template 表是租户侧保存角色时报
-- 「角色授权包含租户权限模板之外的菜单」。
--
-- ⚠️ menu_id 一律按 **auth_code 反查真实 id**，不写死 2410/241xxx：
--    菜单 id 被占用时插入会被守卫跳过，写死 id 就会把权限白授给别人的菜单（实测）。
-- 用 INSERT ... SELECT WHERE NOT EXISTS（去掉 INSERT IGNORE）：守卫列就是主键两列，
-- IGNORE 在此只是冗余，而它会掩盖守卫之外的冲突。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                      'system:space:delete', 'system:space:bind')
  AND m.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                      'system:space:delete', 'system:space:bind')
  AND m.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_template_menu tm WHERE tm.template_id = 1 AND tm.menu_id = m.id);

-- ---------- 6) 自检：漏装即失败（不静默） ----------
-- 迁移脚本最大的风险是「报了成功、实际没装上」。这里做最小完备性断言：
-- 空间类型字典项必须 >= 10、空间菜单必须 = 5。任一不成立直接报错终止。
-- （用 SIGNAL 而不是 SELECT 输出，是为了让 mysql 客户端以非零退出码结束，脚本能停。）

SET @space_menu_cnt := (SELECT COUNT(*) FROM sys_menu
    WHERE auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                        'system:space:delete', 'system:space:bind') AND is_deleted = 0);
SET @space_type_cnt := (SELECT COUNT(*) FROM sys_dict_item i
    INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.is_deleted = 0);
SET @space_target_cnt := (SELECT COUNT(*) FROM sys_dict_item i
    INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.is_deleted = 0);

-- 5 个菜单；空间类型 >= 10（允许管理员后续自行增删，所以是下界）；关联类型 >= 5
SET @msg := CONCAT('空间迁移自检失败：menus=', @space_menu_cnt,
                   ' space_type_items=', @space_type_cnt,
                   ' target_type_items=', @space_target_cnt,
                   '（期望 menus=5、space_type_items>=10、target_type_items>=5）');
SET @fail := (@space_menu_cnt <> 5 OR @space_type_cnt < 10 OR @space_target_cnt < 5);
-- 用动态 SQL 触发 SIGNAL：MySQL 的 SIGNAL 不能在普通 IF 里带变量文案，故拼语句
SET @stmt := IF(@fail,
    CONCAT("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '", REPLACE(@msg, '''', ''), "'"),
    'DO 0');
PREPARE check_space FROM @stmt;
EXECUTE check_space;
DEALLOCATE PREPARE check_space;


-- =============================================================
-- 种子合并：空间关联对象类型补充「岗位」（原 V9）
-- =============================================================

-- 空间关联对象类型补充「岗位」（post）。
--
-- 背景：前端岗位管理（/system/post）复用空间关联面板，调用 targetType=post；
-- 而 V8 的空间种子只写入了 dept/user/device/asset/other，
-- SysSpaceRelationServiceImpl#normalizeTargetType 按字典 space_target_type 校验时
-- 会把 post 判为非法（409「关联对象类型不合法」）。
--
-- 按「字典是取值单一来源」的设计，新增业务对象类型只需补字典项，空间侧零代码。
-- 刻意不改 V8：已应用的迁移不能改校验和；新库也会按顺序执行本迁移。
-- 幂等：重复执行时 WHERE NOT EXISTS 会跳过。

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'),
       '岗位', 'post', 'processing', 5, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id
                  WHERE d.code = 'space_target_type' AND i.value = 'post');

-- =============================================================
-- 演示数据：空间管理（与上方演示用户/部门/岗位配套）
-- 园区 → 楼栋 → 楼层 → 房间；固定 ID（9001+）便于关联与排查，tenant_id=1
-- 仅开发/演示环境使用；生产部署请按需清理
-- =============================================================
INSERT INTO sys_space (id, tenant_id, pid, name, code, space_type, level, sort, address,
                       floor_no, room_no, area, capacity, manager, phone, status, remark,
                       create_user, create_time, is_deleted)
VALUES
(9001, 1, 0,    '总部园区', 'HQ-PARK',  'park',         1, 1, '北京市朝阳区望京街 1 号',
 NULL, NULL,  NULL,    NULL, NULL,  NULL,          1, '演示园区', 1, NOW(), 0),
(9002, 1, 9001, 'A栋',     'HQ-A',     'building',     2, 1, NULL,
 NULL, NULL,  NULL,    NULL, NULL,  NULL,          1, NULL,     1, NOW(), 0),
(9003, 1, 9002, 'A栋1F',   'HQ-A-1F',  'floor',        3, 1, NULL,
 '1F', NULL,  NULL,    NULL, NULL,  NULL,          1, NULL,     1, NOW(), 0),
(9004, 1, 9003, '101办公室', 'HQ-A-101', 'office',       4, 1, NULL,
 NULL, '101', 60.00,   8,    '张三', '13800000001', 1, NULL,     1, NOW(), 0),
(9005, 1, 9003, '102会议室', 'HQ-A-102', 'meeting_room', 4, 2, NULL,
 NULL, '102', 80.00,   20,   NULL,   NULL,          1, NULL,     1, NOW(), 0),
(9006, 1, 9003, '103机房',   'HQ-A-103', 'server_room',  4, 3, NULL,
 NULL, '103', 30.00,   5,    NULL,   NULL,          1, NULL,     1, NOW(), 0),
(9007, 1, 9001, '中心仓库', 'HQ-WH',    'warehouse',    2, 2, NULL,
 NULL, NULL,  500.00,  NULL, NULL,   NULL,          1, NULL,     1, NOW(), 0),
(9008, 1, 9001, '员工宿舍', 'HQ-DORM',  'dormitory',    2, 3, NULL,
 NULL, NULL,  300.00,  40,   NULL,   NULL,          1, NULL,     1, NOW(), 0);

-- 空间与业务对象关联：101 办公室挂研发中心 + 研发工程师岗位；102 会议室挂产品中心
INSERT INTO sys_space_relation (id, tenant_id, space_id, target_type, target_id, is_primary,
                                remark, create_user, create_time, status, is_deleted)
VALUES
(9101, 1, 9004, 'dept', 2,  1, '研发中心办公',   1, NOW(), 1, 0),
(9102, 1, 9004, 'post', 13, 1, '研发工程师岗位', 1, NOW(), 1, 0),
(9103, 1, 9005, 'dept', 3,  1, NULL,             1, NOW(), 1, 0);
