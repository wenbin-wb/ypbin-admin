-- =============================================================
-- ypbin-admin 种子数据（开发阶段整合版，菜单树为最终归类结构）
-- 默认租户 1；超级管理员 admin/admin123（BCrypt）；super 角色跳过权限校验
-- 固定 ID 便于关联，雪花 ID 从业务新增时生成
-- =============================================================

-- 默认权限模板（全部权限，稍后授权所有菜单）
INSERT INTO sys_auth_template (id, name, code, remark, create_user, create_time, status, is_deleted)
VALUES (1, '全部权限', 'ALL', '内置模板：拥有全部菜单权限', 1, NOW(), 1, 0);

-- 默认租户（绑定全部权限模板）
INSERT INTO sys_tenant (id, name, code, template_id, contact_name, remark, create_time, status, is_deleted)
VALUES (1, '默认租户', 'default', 1, 'admin', '系统内置默认租户', NOW(), 1, 0);

-- 部门树（测试数据）：长沙词云信息科技为总公司，下设职能与业务部门
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

-- 超级管理员 admin/admin123
INSERT INTO sys_user (id, tenant_id, username, password, real_name, nickname, dept_id, gender,
                      remark, pwd_reset_time, create_time, status, is_deleted)
VALUES (1, 1, 'admin', '$2a$10$ZuXfY6FkrI0fEGRoX9AlZuo3r/askEJEVHz6rKwKMrDVCpttLIq82',
        '超级管理员', '超级管理员', 1, 1, '系统内置超级管理员', NOW(), NOW(), 1, 0);

-- 超级管理员角色（super 跳过权限校验）
INSERT INTO sys_role (id, tenant_id, name, code, data_scope, sort, remark, create_time, status, is_deleted)
VALUES (1, 1, '超级管理员', 'super', 1, 1, '系统内置超级管理员角色，拥有全部权限', NOW(), 1, 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 内置字典：状态、性别
INSERT INTO sys_dict (id, name, code, remark, create_user, create_time, status, is_deleted)
VALUES (1, '系统状态', 'sys_status', '通用启用/禁用状态', 1, NOW(), 1, 0),
       (2, '性别', 'sys_gender', '用户性别', 1, NOW(), 1, 0);

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
VALUES (11, 1, '正常', '1', 'success', 1, 1, NOW(), 1, 0),
       (12, 1, '禁用', '0', 'error', 2, 1, NOW(), 1, 0),
       (21, 2, '未知', '0', 'default', 1, 1, NOW(), 1, 0),
       (22, 2, '男', '1', 'processing', 2, 1, NOW(), 1, 0),
       (23, 2, '女', '2', 'warning', 3, 1, NOW(), 1, 0);

-- 示例任务：清理临时文件（默认停用）
INSERT INTO sys_job (id, name, executor, cron, create_user, create_time, status, is_deleted)
VALUES (1, '清理临时文件', 'cleanTempFile', '0 0 3 * * ?', 1, NOW(), 0, 0);

-- 默认 Web 管理后台客户端
INSERT INTO sys_client (id, client_id, client_type, auth_types, timeout, active_timeout, concurrent_enabled, create_user, create_time, status, is_deleted)
VALUES (1, 'web-admin', 'WEB', 'ACCOUNT,PHONE,EMAIL', 86400, 1800, 1, 1, NOW(), 1, 0);

-- =============================================================
-- 测试数据（方便页面调试；密码统一 admin123）
-- =============================================================
-- 岗位
INSERT INTO sys_post (id, tenant_id, name, code, category, sort, remark, create_user, create_time, status, is_deleted)
VALUES (11, 1, '总经理', 'CEO', '管理', 1, NULL, 1, NOW(), 1, 0),
       (12, 1, '技术总监', 'CTO', '管理', 2, NULL, 1, NOW(), 1, 0),
       (13, 1, '研发工程师', 'DEV', '技术', 3, NULL, 1, NOW(), 1, 0),
       (14, 1, '产品经理', 'PM', '产品', 4, NULL, 1, NOW(), 1, 0),
       (15, 1, '测试工程师', 'QA', '技术', 5, NULL, 1, NOW(), 1, 0),
       (16, 1, '人事专员', 'HR', '职能', 6, NULL, 1, NOW(), 1, 0);

-- 测试角色（super 之外的业务角色）
INSERT INTO sys_role (id, tenant_id, name, code, data_scope, sort, remark, create_user, create_time, status, is_deleted)
VALUES (2, 1, '管理员', 'admin', 1, 2, '拥有大部分管理权限', 1, NOW(), 1, 0),
       (3, 1, '普通员工', 'staff', 4, 3, '仅本人数据权限', 1, NOW(), 1, 0);

-- 测试用户（密码均为 admin123）
INSERT INTO sys_user (id, tenant_id, username, password, real_name, nickname, dept_id, phone, email, gender,
                      remark, pwd_reset_time, create_user, create_time, status, is_deleted)
VALUES (2, 1, 'lilei', '$2a$10$ZuXfY6FkrI0fEGRoX9AlZuo3r/askEJEVHz6rKwKMrDVCpttLIq82',
        '李强', '强哥', 2, '13900000001', 'liqiang@ciyun.com', 1, '研发中心负责人', NOW(), 1, NOW(), 1, 0),
       (3, 1, 'wangfang', '$2a$10$ZuXfY6FkrI0fEGRoX9AlZuo3r/askEJEVHz6rKwKMrDVCpttLIq82',
        '王芳', '芳芳', 3, '13900000002', 'wangfang@ciyun.com', 2, '产品中心负责人', NOW(), 1, NOW(), 1, 0),
       (4, 1, 'liuyang', '$2a$10$ZuXfY6FkrI0fEGRoX9AlZuo3r/askEJEVHz6rKwKMrDVCpttLIq82',
        '刘洋', '洋洋', 6, '13900000003', 'liuyang@ciyun.com', 1, '后端组开发', NOW(), 1, NOW(), 1, 0),
       (5, 1, 'zhoutao', '$2a$10$ZuXfY6FkrI0fEGRoX9AlZuo3r/askEJEVHz6rKwKMrDVCpttLIq82',
        '周涛', '涛哥', 8, '13900000004', 'zhoutao@ciyun.com', 1, '测试组', NOW(), 1, NOW(), 0, 0);

-- 测试用户-角色
INSERT INTO sys_user_role (user_id, role_id)
VALUES (2, 2), (3, 2), (4, 3), (5, 3);

-- 测试用户-岗位
INSERT INTO sys_user_post (user_id, post_id)
VALUES (2, 12), (3, 14), (4, 13), (5, 15);

-- 测试公告（不同状态/类型/范围）
INSERT INTO sys_notice (id, title, content, notice_type, notice_scope, scope_target_ids, notify_methods, is_top, publish_type, publish_status, publish_time, create_user, create_time, status, is_deleted)
VALUES (1, '系统上线通知', '<p>词云信息科技管理后台正式上线，欢迎大家使用。</p>', 1, 1, NULL, 'site', 1, 1, 2, NOW(), 1, NOW(), 1, 0),
       (2, '五一放假安排', '<p>5 月 1 日至 5 月 5 日放假，共 5 天。</p>', 2, 1, NULL, 'site,email', 0, 1, 2, NOW(), 1, NOW(), 1, 0),
       (3, '研发中心周会通知', '<p>本周五下午 3 点研发中心全员周会。</p>', 1, 3, '2', 'site', 0, 1, 2, NOW(), 1, NOW(), 1, 0),
       (4, '年终总结大会（草稿）', '<p>年终总结大会筹备中……</p>', 2, 1, NULL, 'site', 0, 2, 0, NULL, 1, NOW(), 1, 0);

-- =============================================================
-- 系统参数
-- =============================================================
-- 站点配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (1, 'site', '系统名称', 'SITE_NAME', 'ypbin-admin', 1, 1, NOW(), 1, 0),
       (2, 'site', '版权信息', 'SITE_COPYRIGHT', 'ypbin', 1, 1, NOW(), 1, 0);

-- 登录配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (10, 'login', '是否开启登录验证码', 'LOGIN_CAPTCHA_ENABLED', 'false', 1, 1, NOW(), 1, 0),
       (11, 'login', '是否开启短信验证码登录', 'LOGIN_SMS_ENABLED', 'false', 1, 1, NOW(), 1, 0);

-- 密码策略配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (20, 'password', '密码最小长度', 'PASSWORD_MIN_LENGTH', '8', 1, 1, NOW(), 1, 0),
       (21, 'password', '是否必须含数字', 'PASSWORD_REQUIRE_DIGIT', 'true', 1, 1, NOW(), 1, 0),
       (22, 'password', '是否必须含字母', 'PASSWORD_REQUIRE_LETTER', 'true', 1, 1, NOW(), 1, 0),
       (23, 'password', '是否必须含特殊字符', 'PASSWORD_REQUIRE_SYMBOL', 'false', 1, 1, NOW(), 1, 0),
       (24, 'password', '是否允许含用户名', 'PASSWORD_ALLOW_CONTAIN_USERNAME', 'false', 1, 1, NOW(), 1, 0),
       (25, 'password', '登录错误锁定阈值', 'PASSWORD_ERROR_LOCK_COUNT', '5', 1, 1, NOW(), 1, 0),
       (26, 'password', '账号锁定时长(分钟)', 'PASSWORD_LOCK_MINUTES', '15', 1, 1, NOW(), 1, 0),
       (27, 'password', '密码有效期(天)', 'PASSWORD_EXPIRATION_DAYS', '0', 1, 1, NOW(), 1, 0),
       (28, 'password', '历史密码不可重复次数', 'PASSWORD_HISTORY_COUNT', '0', 1, 1, NOW(), 1, 0);

-- 短信配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (30, 'sms', '短信厂商', 'SMS_SUPPLIER', '', 1, 1, NOW(), 1, 0),
       (31, 'sms', 'AccessKeyId', 'SMS_ACCESS_KEY_ID', '', 1, 1, NOW(), 1, 0),
       (32, 'sms', 'AccessKeySecret', 'SMS_ACCESS_KEY_SECRET', '', 1, 1, NOW(), 1, 0),
       (33, 'sms', '短信签名', 'SMS_SIGNATURE', '', 1, 1, NOW(), 1, 0),
       (34, 'sms', '验证码模板ID', 'SMS_TEMPLATE_ID', '', 1, 1, NOW(), 1, 0),
       (35, 'sms', '验证码有效期(秒)', 'SMS_CODE_EXPIRE_SECONDS', '300', 1, 1, NOW(), 1, 0);

-- 邮件配置
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (40, 'mail', 'SMTP 服务器', 'MAIL_HOST', '', 1, 1, NOW(), 1, 0),
       (41, 'mail', 'SMTP 端口', 'MAIL_PORT', '465', 1, 1, NOW(), 1, 0),
       (42, 'mail', '邮箱账号', 'MAIL_USERNAME', '', 1, 1, NOW(), 1, 0),
       (43, 'mail', '邮箱密码/授权码', 'MAIL_PASSWORD', '', 1, 1, NOW(), 1, 0),
       (44, 'mail', '发件地址', 'MAIL_FROM', '', 1, 1, NOW(), 1, 0),
       (45, 'mail', '发件人名称', 'MAIL_FROM_NAME', '', 1, 1, NOW(), 1, 0),
       (46, 'mail', '是否 SSL', 'MAIL_SSL_ENABLED', 'true', 1, 1, NOW(), 1, 0);

-- 第三方登录配置（各平台需填写对应 clientId/clientSecret/redirectUri 后生效）
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, create_user, create_time, status, is_deleted)
VALUES (50, 'social', 'GitHub ClientId', 'SOCIAL_GITHUB_CLIENT_ID', '', 1, 1, NOW(), 1, 0),
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
       (67, 'social', '钉钉 回调地址', 'SOCIAL_DINGTALK_REDIRECT_URI', '', 1, 1, NOW(), 1, 0);

-- =============================================================
-- 菜单树（顶级为 6 个分类目录 + 仪表盘；业务菜单直接挂到对应分类下）
-- 分类目录：3001 组织 / 3002 权限 / 3003 系统 / 3004 监控 / 3005 租户 / 3006 任务
-- =============================================================

-- 仪表盘
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (1, 0, 'Dashboard', 'catalog', '/dashboard', 'BasicLayout', 'page.dashboard.title', 'lucide:layout-dashboard', -1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, keep_alive, sort, create_time, status, is_deleted)
VALUES (101, 1, 'Analytics', 'menu', '/dashboard/analytics', '/dashboard/analytics/index', 'page.dashboard.analytics', 'lucide:area-chart', 1, 1, NOW(), 1, 0),
       (102, 1, 'Workspace', 'menu', '/dashboard/workspace', '/dashboard/workspace/index', 'page.dashboard.workspace', 'carbon:workspace', 0, 2, NOW(), 1, 0);

-- 顶级分类目录 + 一级单页菜单
-- 一级顺序：仪表盘 / 组织 / 权限 / 租户 / 任务调度 / 文件 / 通知 / 监控 / 系统
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (3001, 0, 'OrgManage', 'catalog', '/system/org', 'BasicLayout', 'system.org.title', 'carbon:tree-view-alt', 1, NOW(), 1, 0),
       (3002, 0, 'AuthManage', 'catalog', '/system/auth', 'BasicLayout', 'system.auth.title', 'carbon:security', 2, NOW(), 1, 0),
       (3005, 0, 'TenantManage', 'catalog', '/system/tenant', 'BasicLayout', 'system.tenant.title', 'carbon:building', 3, NOW(), 1, 0),
       (3006, 0, 'JobManage', 'catalog', '/system/jobm', 'BasicLayout', 'system.schedule.title', 'carbon:timer', 4, NOW(), 1, 0),
       (3004, 0, 'MonitorManage', 'catalog', '/system/monitor', 'BasicLayout', 'system.monitor.title', 'carbon:activity', 7, NOW(), 1, 0),
       (3003, 0, 'SysManage', 'catalog', '/system/sys', 'BasicLayout', 'system.sys.title', 'carbon:settings-adjust', 8, NOW(), 1, 0);

-- 文件管理、通知公告：提到一级（单页菜单，框架自动套 BasicLayout 外壳）
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2600, 0, 'SystemFile', 'menu', '/system/file', '/system/file/list', 'system:file:list', 'system.file.title', 'carbon:document-attachment', 5, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (260001, 2600, 'SystemFileUpload', 'button', 'system:file:upload', 'common.upload', 1, NOW(), 1, 0),
       (260002, 2600, 'SystemFileDelete', 'button', 'system:file:delete', 'common.delete', 2, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2700, 0, 'SystemNotice', 'menu', '/system/notice', '/system/notice/list', 'system:notice:list', 'system.notice.title', 'carbon:notification', 6, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (270001, 2700, 'SystemNoticeAdd', 'button', 'system:notice:add', 'common.create', 1, NOW(), 1, 0),
       (270002, 2700, 'SystemNoticeEdit', 'button', 'system:notice:edit', 'common.edit', 2, NOW(), 1, 0),
       (270003, 2700, 'SystemNoticeDelete', 'button', 'system:notice:delete', 'common.delete', 3, NOW(), 1, 0);

-- 组织管理（3001）：用户 / 部门 / 岗位
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (210, 3001, 'SystemUser', 'menu', '/system/user', '/system/user/list', 'system:user:list', 'system.user.title', 'carbon:user', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (21001, 210, 'SystemUserAdd', 'button', 'system:user:add', 'common.create', 1, NOW(), 1, 0),
       (21002, 210, 'SystemUserEdit', 'button', 'system:user:edit', 'common.edit', 2, NOW(), 1, 0),
       (21003, 210, 'SystemUserDelete', 'button', 'system:user:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (240, 3001, 'SystemDept', 'menu', '/system/dept', '/system/dept/list', 'system:dept:list', 'system.dept.title', 'carbon:container-services', 4, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (24001, 240, 'SystemDeptAdd', 'button', 'system:dept:add', 'common.create', 1, NOW(), 1, 0),
       (24002, 240, 'SystemDeptEdit', 'button', 'system:dept:edit', 'common.edit', 2, NOW(), 1, 0),
       (24003, 240, 'SystemDeptDelete', 'button', 'system:dept:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2500, 3001, 'SystemPost', 'menu', '/system/post', '/system/post/list', 'system:post:list', 'system.post.title', 'carbon:id-management', 10, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (250001, 2500, 'SystemPostAdd', 'button', 'system:post:add', 'common.create', 1, NOW(), 1, 0),
       (250002, 2500, 'SystemPostEdit', 'button', 'system:post:edit', 'common.edit', 2, NOW(), 1, 0),
       (250003, 2500, 'SystemPostDelete', 'button', 'system:post:delete', 'common.delete', 3, NOW(), 1, 0);

-- 权限管理（3002）：角色 / 菜单 / 客户端 / 开放应用 / 权限模板
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (220, 3002, 'SystemRole', 'menu', '/system/role', '/system/role/list', 'system:role:list', 'system.role.title', 'carbon:user-role', 2, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (22001, 220, 'SystemRoleAdd', 'button', 'system:role:add', 'common.create', 1, NOW(), 1, 0),
       (22002, 220, 'SystemRoleEdit', 'button', 'system:role:edit', 'common.edit', 2, NOW(), 1, 0),
       (22003, 220, 'SystemRoleDelete', 'button', 'system:role:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (230, 3002, 'SystemMenu', 'menu', '/system/menu', '/system/menu/list', 'system:menu:list', 'system.menu.title', 'carbon:menu', 3, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (23001, 230, 'SystemMenuAdd', 'button', 'system:menu:add', 'common.create', 1, NOW(), 1, 0),
       (23002, 230, 'SystemMenuEdit', 'button', 'system:menu:edit', 'common.edit', 2, NOW(), 1, 0),
       (23003, 230, 'SystemMenuDelete', 'button', 'system:menu:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (290, 3002, 'SystemClient', 'menu', '/system/client', '/system/client/list', 'system:client:list', 'system.client.title', 'carbon:application', 9, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (29001, 290, 'SystemClientAdd', 'button', 'system:client:add', 'common.create', 1, NOW(), 1, 0),
       (29002, 290, 'SystemClientEdit', 'button', 'system:client:edit', 'common.edit', 2, NOW(), 1, 0),
       (29003, 290, 'SystemClientDelete', 'button', 'system:client:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2900, 3002, 'SystemApp', 'menu', '/system/app', '/system/app/list', 'system:app:list', 'system.app.title', 'carbon:api', 14, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (290001, 2900, 'SystemAppAdd', 'button', 'system:app:add', 'common.create', 1, NOW(), 1, 0),
       (290002, 2900, 'SystemAppEdit', 'button', 'system:app:edit', 'common.edit', 2, NOW(), 1, 0),
       (290003, 2900, 'SystemAppDelete', 'button', 'system:app:delete', 'common.delete', 3, NOW(), 1, 0);

-- 系统管理（3003，排最后）：字典 / 参数
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (250, 3003, 'SystemDict', 'menu', '/system/dict', '/system/dict/list', 'system:dict:list', 'system.dict.title', 'carbon:catalog', 5, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (25001, 250, 'SystemDictAdd', 'button', 'system:dict:add', 'common.create', 1, NOW(), 1, 0),
       (25002, 250, 'SystemDictEdit', 'button', 'system:dict:edit', 'common.edit', 2, NOW(), 1, 0),
       (25003, 250, 'SystemDictDelete', 'button', 'system:dict:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (260, 3003, 'SystemConfig', 'menu', '/system/config', '/system/config/index', 'system:config:list', 'system.config.title', 'carbon:settings-adjust', 6, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (26001, 260, 'SystemConfigAdd', 'button', 'system:config:add', 'common.create', 1, NOW(), 1, 0),
       (26002, 260, 'SystemConfigEdit', 'button', 'system:config:edit', 'common.edit', 2, NOW(), 1, 0),
       (26003, 260, 'SystemConfigDelete', 'button', 'system:config:delete', 'common.delete', 3, NOW(), 1, 0);

-- 监控管理（3004）：日志 / 在线用户
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (270, 3004, 'SystemLog', 'menu', '/system/log', '/system/log/list', 'system:log:list', 'system.log.title', 'carbon:document', 7, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (27001, 270, 'SystemLogExport', 'button', 'system:log:export', 'common.export', 1, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (280, 3004, 'SystemOnlineUser', 'menu', '/system/online-user', '/system/online-user/list', 'system:online-user:list', 'system.onlineUser.title', 'carbon:user-online', 8, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (28001, 280, 'SystemOnlineUserKickout', 'button', 'system:online-user:kickout', 'common.kickout', 1, NOW(), 1, 0);

-- 租户管理（3005）：租户列表 / 权限模板（权限模板是分配给租户的菜单权限集，归此目录）
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2950, 3005, 'SystemTenant', 'menu', '/system/tenant', '/system/tenant/list', 'system:tenant:list', 'system.tenant.list', 'carbon:building-insights-2', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (295001, 2950, 'SystemTenantAdd', 'button', 'system:tenant:add', 'common.create', 1, NOW(), 1, 0),
       (295002, 2950, 'SystemTenantEdit', 'button', 'system:tenant:edit', 'common.edit', 2, NOW(), 1, 0),
       (295003, 2950, 'SystemTenantDelete', 'button', 'system:tenant:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2952, 3005, 'SystemAuthTemplate', 'menu', '/system/auth-template', '/system/auth-template/list', 'system:auth-template:list', 'system.authTemplate.title', 'carbon:template', 2, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (295201, 2952, 'SystemAuthTemplateAdd', 'button', 'system:auth-template:add', 'common.create', 1, NOW(), 1, 0),
       (295202, 2952, 'SystemAuthTemplateEdit', 'button', 'system:auth-template:edit', 'common.edit', 2, NOW(), 1, 0),
       (295203, 2952, 'SystemAuthTemplateDelete', 'button', 'system:auth-template:delete', 'common.delete', 3, NOW(), 1, 0);

-- 任务管理（3006）：定时任务 / 执行日志（汇总页）
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2800, 3006, 'SystemJob', 'menu', '/system/job', '/system/job/list', 'system:job:list', 'system.job.title', 'carbon:timer', 13, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (280001, 2800, 'SystemJobAdd', 'button', 'system:job:add', 'common.create', 1, NOW(), 1, 0),
       (280002, 2800, 'SystemJobEdit', 'button', 'system:job:edit', 'common.edit', 2, NOW(), 1, 0),
       (280003, 2800, 'SystemJobDelete', 'button', 'system:job:delete', 'common.delete', 3, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2951, 3006, 'SystemJobLog', 'menu', '/system/job/log', '/system/job/log/list', 'system:job:list', 'system.jobLog.title', 'carbon:document', 2, NOW(), 1, 0);

-- =============================================================
-- 全部权限模板授权所有菜单（默认租户拥有全部菜单权限）
-- =============================================================
INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, id FROM sys_menu WHERE is_deleted = 0;
