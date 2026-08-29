-- ============================================================
-- ypbin-admin 演示数据脚本（手动执行，非种子数据）
--
-- 用途：为截图/演示填充大量真实感数据（部门、用户、角色、字典、
--       公告、消息、任务、日志等），让界面看起来数据丰富、正式。
--
-- 用法（手动执行，不进 Flyway 迁移）：
--   mysql --default-character-set=utf8mb4 -u<user> -p<pass> ypbin_admin < deploy/demo-data.sql
--   # 或容器内：
--   docker exec -i deploy-mysql-1 mysql --default-character-set=utf8mb4 -uroot -p<pass> ypbin_admin < deploy/demo-data.sql
--
-- 特性：
--   · 幂等：可重复执行，执行前自动清理本脚本产生的数据
--   · 主键范围 1000+，与种子数据（<100）不冲突
--   · 演示用户统一密码 123456（BCrypt，与种子用户一致）
--   · 内置 SET NAMES utf8mb4，避免中文乱码
--
-- 注意：仅用于演示/截图环境，生产环境请勿执行。
-- ============================================================

-- 强制 utf8mb4 字符集（防止中文乱码）
SET NAMES utf8mb4;

-- ============ 0. 幂等清理（按演示主键范围） ============
DELETE FROM sys_user_role       WHERE user_id >= 1000;
DELETE FROM sys_user_post       WHERE user_id >= 1000;
DELETE FROM sys_role_dept       WHERE role_id >= 100;
DELETE FROM sys_role_menu       WHERE role_id >= 100;
DELETE FROM sys_message         WHERE id >= 1000;
DELETE FROM sys_notice_delivery WHERE id >= 1000;
DELETE FROM sys_notice          WHERE id >= 1000;
DELETE FROM sys_job_log         WHERE id >= 1000;
DELETE FROM sys_job             WHERE id >= 1000;
DELETE FROM sys_log             WHERE id >= 1000;
DELETE FROM sys_file            WHERE id >= 1000;
DELETE FROM sys_config          WHERE id >= 1000;
DELETE FROM sys_dict_item       WHERE id >= 1000;
DELETE FROM sys_dict            WHERE id >= 100;
DELETE FROM sys_post            WHERE id >= 1000;
DELETE FROM sys_user            WHERE id >= 1000;
DELETE FROM sys_dept            WHERE id >= 100;
DELETE FROM sys_role            WHERE id >= 100;
DELETE FROM sys_app             WHERE id >= 1000;
DELETE FROM sys_user_social     WHERE id >= 1000;
DELETE FROM sys_tenant          WHERE id >= 100;
DELETE FROM sys_license         WHERE id >= 1000;
DELETE FROM ai_model_config     WHERE id >= 100;
DELETE FROM ai_knowledge_base   WHERE id >= 100;
DELETE FROM ai_document         WHERE id >= 100;
DELETE FROM ai_document_chunk   WHERE id >= 100;
DELETE FROM ai_prompt_template  WHERE id >= 100;
DELETE FROM ai_usage_log        WHERE id >= 100;
DELETE FROM ai_query_log        WHERE id >= 100;
DELETE FROM ai_chat_session     WHERE id >= 100;
DELETE FROM ai_chat_message     WHERE id >= 100;
DELETE FROM ai_chat_role        WHERE id >= 100;
DELETE FROM ai_chat_role_favorite WHERE id >= 100;

-- ============ 1. 部门（扩展组织树） ============
INSERT INTO sys_dept (id, tenant_id, pid, name, sort, leader, phone, email, remark, create_user, create_time, status, is_deleted) VALUES
(100, 1, 1,   '行政部',   5,  '刘芳', '13700001001', 'admin@ciyun.com',     '行政人事综合', 1, NOW(), 1, 0),
(101, 1, 1,   '财务部',   6,  '陈静', '13700001002', 'finance@ciyun.com',   '财务管理',     1, NOW(), 1, 0),
(102, 1, 1,   '运营部',   7,  '杨帆', '13700001003', 'ops@ciyun.com',       '平台运营',     1, NOW(), 1, 0),
(103, 1, 1,   '客服部',   8,  '周丽', '13700001004', 'support@ciyun.com',   '客户服务',     1, NOW(), 1, 0),
(104, 1, 2,   '研发一组', 1,  '李强', '13800000011', 'rd1@ciyun.com',       '后端研发',     1, NOW(), 1, 0),
(105, 1, 2,   '研发二组', 2,  '张涛', '13800000012', 'rd2@ciyun.com',       '前端研发',     1, NOW(), 1, 0),
(106, 1, 2,   '测试组',   3,  '孙婷', '13800000013', 'qa@ciyun.com',        '质量保障',     1, NOW(), 1, 0),
(107, 1, 2,   '运维组',   4,  '郑强', '13800000014', 'devops@ciyun.com',    '系统运维',     1, NOW(), 1, 0),
(108, 1, 3,   '产品一组', 1,  '王芳', '13800000021', 'pm1@ciyun.com',       '产品设计',     1, NOW(), 1, 0),
(109, 1, 3,   'UI 设计组',2,  '吴倩', '13800000022', 'ui@ciyun.com',        '视觉设计',     1, NOW(), 1, 0),
(110, 1, 4,   '市场推广组',1, '赵敏', '13800000031', 'mkt@ciyun.com',       '市场推广',     1, NOW(), 1, 0),
(111, 1, 4,   '商务合作组',2, '钱进', '13800000032', 'biz@ciyun.com',       '商务合作',     1, NOW(), 1, 0);

-- ============ 2. 角色 ============
INSERT INTO sys_role (id, tenant_id, name, code, role_type, data_scope, sort, remark, create_user, create_time, status, is_deleted) VALUES
(100, 1, '研发工程师',   'rd_engineer',   'TENANT_ROLE', 2, 1, '研发工程师角色', 1, NOW(), 1, 0),
(101, 1, '产品经理',     'product_mgr',   'TENANT_ROLE', 3, 2, '产品经理角色',   1, NOW(), 1, 0),
(102, 1, '测试工程师',   'qa_engineer',   'TENANT_ROLE', 4, 3, '测试工程师角色', 1, NOW(), 1, 0),
(103, 1, '运营专员',     'ops_staff',     'TENANT_ROLE', 2, 4, '运营专员角色',   1, NOW(), 1, 0),
(104, 1, '财务专员',     'finance_staff', 'TENANT_ROLE', 4, 5, '财务专员角色',   1, NOW(), 1, 0),
(105, 1, '客服专员',     'support_staff', 'TENANT_ROLE', 4, 6, '客服专员角色',   1, NOW(), 1, 0);

-- 角色-部门（数据范围：本部门及以下）
INSERT INTO sys_role_dept (role_id, dept_id) VALUES
(100, 104), (100, 105), (101, 108), (101, 109), (102, 106), (103, 102), (104, 101), (105, 103);

-- 角色-菜单（复用种子菜单，保证演示角色有完整菜单可见）
-- 目录：1 Dashboard / 3001 组织 / 3002 认证 / 3003 系统 / 3004 监控 / 3005 租户 / 3006 任务 / 3007 消息 / 3008 授权 / 5000 AI
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
-- 研发工程师：Dashboard + 组织 + 系统 + 任务 + AI（含 AI 按钮级权限）
(100, 1), (100, 101), (100, 102), (100, 3001), (100, 210), (100, 21001), (100, 21002), (100, 21003),
(100, 220), (100, 240), (100, 250), (100, 260), (100, 3003), (100, 270), (100, 280),
(100, 3006), (100, 2800), (100, 2951), (100, 5000), (100, 5001), (100, 5011), (100, 5012), (100, 5013), (100, 5014), (100, 5015), (100, 5002), (100, 5021), (100, 5022), (100, 5023), (100, 5024), (100, 5025), (100, 5003), (100, 5031), (100, 5032), (100, 5033), (100, 5034),
-- 产品经理：Dashboard + 组织 + 系统 + AI 对话
(101, 1), (101, 101), (101, 102), (101, 3001), (101, 210), (101, 220), (101, 240), (101, 250),
(101, 3003), (101, 270), (101, 5000), (101, 5001), (101, 5011), (101, 5012), (101, 5002),
-- 测试工程师：Dashboard + 系统 + 任务
(102, 1), (102, 101), (102, 3001), (102, 210), (102, 3003), (102, 270), (102, 3006), (102, 2800), (102, 2951),
-- 运营专员：Dashboard + 消息 + 公告
(103, 1), (103, 101), (103, 3007), (103, 2700), (103, 270001), (103, 270002), (103, 4000),
-- 财务专员：Dashboard + 系统参数
(104, 1), (104, 101), (104, 3003), (104, 260), (104, 26001), (104, 26002),
-- 客服专员：Dashboard + 消息
(105, 1), (105, 101), (105, 3007), (105, 2700), (105, 4000);

-- ============ 3. 岗位 ============
INSERT INTO sys_post (id, tenant_id, name, code, category, sort, remark, create_user, create_time, status, is_deleted) VALUES
(1000, 1, '高级后端工程师', 'senior_be_dev',   '技术', 1, '后端研发岗', 1, NOW(), 1, 0),
(1001, 1, '高级前端工程师', 'senior_fe_dev',   '技术', 2, '前端研发岗', 1, NOW(), 1, 0),
(1002, 1, '测试主管',       'qa_leader',       '技术', 3, '测试管理岗', 1, NOW(), 1, 0),
(1003, 1, '产品经理',       'product_manager', '产品', 4, '产品岗',     1, NOW(), 1, 0),
(1004, 1, 'UI 设计师',      'ui_designer',     '产品', 5, '设计岗',     1, NOW(), 1, 0),
(1005, 1, '运营经理',       'ops_manager',     '运营', 6, '运营管理岗', 1, NOW(), 1, 0),
(1006, 1, '财务主管',       'finance_leader',  '财务', 7, '财务管理岗', 1, NOW(), 1, 0),
(1007, 1, '客服主管',       'support_leader',  '客服', 8, '客服管理岗', 1, NOW(), 1, 0);

-- ============ 4. 用户（演示用户，密码统一 123456） ============
-- BCrypt('123456') = $2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW
INSERT INTO sys_user (id, tenant_id, username, user_type, password, real_name, nickname, dept_id, avatar, phone, email, gender, remark, last_login_time, pwd_reset_time, create_user, create_time, status, is_deleted) VALUES
-- 研发部
(1000, 1, 'chenhaoran', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '陈浩然', '浩然', 104, NULL, '13900001001', 'chenhr@ciyun.com', 1, '研发一组组长', DATE_SUB(NOW(), INTERVAL 2 HOUR), NOW(), 1, NOW(), 1, 0),
(1001, 1, 'linjunjie', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '林俊杰', '俊杰', 104, NULL, '13900001002', 'linjj@ciyun.com', 1, '后端开发', DATE_SUB(NOW(), INTERVAL 5 HOUR), NOW(), 1, NOW(), 1, 0),
(1002, 1, 'hexiaoyu', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '何小雨', '小雨', 104, NULL, '13900001003', 'hexy@ciyun.com', 2, '后端开发', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 1, NOW(), 1, 0),
(1003, 1, 'xuzewei', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '徐泽伟', '泽伟', 105, NULL, '13900001004', 'xuzw@ciyun.com', 1, '前端开发', DATE_SUB(NOW(), INTERVAL 3 HOUR), NOW(), 1, NOW(), 1, 0),
(1004, 1, 'wangyu', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '王宇', '小宇', 105, NULL, '13900001005', 'wangyu@ciyun.com', 1, '前端开发', DATE_SUB(NOW(), INTERVAL 6 HOUR), NOW(), 1, NOW(), 1, 0),
(1005, 1, 'sunmeng', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '孙萌', '萌萌', 106, NULL, '13900001006', 'sunmeng@ciyun.com', 2, '测试工程师', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 1, NOW(), 1, 0),
(1006, 1, 'zhengtao', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '郑涛', '阿涛', 107, NULL, '13900001007', 'zhengtao@ciyun.com', 1, '运维工程师', DATE_SUB(NOW(), INTERVAL 4 HOUR), NOW(), 1, NOW(), 1, 0),
-- 产品部
(1007, 1, 'wuqian', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '吴倩', '倩倩', 108, NULL, '13900001008', 'wuqian@ciyun.com', 2, '产品经理', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 1, NOW(), 1, 0),
(1008, 1, 'fengyao', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '冯瑶', '小瑶', 109, NULL, '13900001009', 'fengyao@ciyun.com', 2, 'UI 设计师', DATE_SUB(NOW(), INTERVAL 8 HOUR), NOW(), 1, NOW(), 1, 0),
-- 市场部
(1009, 1, 'qianjin', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '钱进', '大钱', 111, NULL, '13900001010', 'qianjin@ciyun.com', 1, '商务合作', DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 1, NOW(), 1, 0),
(1010, 1, 'gaoyuan', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '高媛', '媛媛', 110, NULL, '13900001011', 'gaoyuan@ciyun.com', 2, '市场推广', DATE_SUB(NOW(), INTERVAL 12 HOUR), NOW(), 1, NOW(), 1, 0),
-- 行政/财务/客服
(1011, 1, 'liufang', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '刘芳', '芳姐', 100, NULL, '13900001012', 'liufang@ciyun.com', 2, '行政主管', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 1, NOW(), 1, 0),
(1012, 1, 'chenjing', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '陈静', '静姐', 101, NULL, '13900001013', 'chenjing@ciyun.com', 2, '财务主管', DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 1, NOW(), 1, 0),
(1013, 1, 'zhouli', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '周丽', '丽丽', 103, NULL, '13900001014', 'zhouli@ciyun.com', 2, '客服主管', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 1, NOW(), 1, 0),
(1014, 1, 'huangjian', 'TENANT', '$2a$10$rFLonGqRDUGIA3EGtNzL4uJN1OHwdQeIwXXjCFpiBUJ0LlOa3kMPW', '黄健', '老黄', 102, NULL, '13900001015', 'huangjian@ciyun.com', 1, '运营经理', DATE_SUB(NOW(), INTERVAL 6 HOUR), NOW(), 1, NOW(), 1, 0);

-- 用户-角色
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1000, 100), (1001, 100), (1002, 100), (1003, 100), (1004, 100),
(1005, 102), (1006, 100), (1007, 101), (1008, 101),
(1009, 103), (1010, 103), (1011, 103), (1012, 104), (1013, 105), (1014, 103);

-- 用户-岗位
INSERT INTO sys_user_post (user_id, post_id) VALUES
(1000, 1000), (1001, 1000), (1002, 1000), (1003, 1001), (1004, 1001),
(1005, 1002), (1006, 1007), (1007, 1003), (1008, 1004),
(1009, 1006), (1010, 1005), (1011, 1005), (1012, 1006), (1013, 1007), (1014, 1005);

-- ============ 5. 字典 ============
INSERT INTO sys_dict (id, name, code, remark, create_user, create_time, status, is_deleted) VALUES
(100, '公告类型', 'notice_type', '公告类型字典', 1, NOW(), 1, 0),
(101, '发布状态', 'publish_status', '公告发布状态', 1, NOW(), 1, 0),
(102, '优先级', 'priority_level', '任务/事项优先级', 1, NOW(), 1, 0),
(103, '证件类型', 'id_type', '身份证件类型', 1, NOW(), 1, 0),
(104, '消息类型', 'message_type', '站内信类型', 1, NOW(), 1, 0);

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, remark, create_user, create_time, status, is_deleted) VALUES
(1000, 100, '通知', '1', 'blue', 1, NULL, 1, NOW(), 1, 0),
(1001, 100, '公告', '2', 'orange', 2, NULL, 1, NOW(), 1, 0),
(1002, 101, '草稿', '0', 'default', 1, NULL, 1, NOW(), 1, 0),
(1003, 101, '待发布', '1', 'warning', 2, NULL, 1, NOW(), 1, 0),
(1004, 101, '已发布', '2', 'success', 3, NULL, 1, NOW(), 1, 0),
(1005, 101, '已撤回', '3', 'danger', 4, NULL, 1, NOW(), 1, 0),
(1006, 102, '低', '1', 'default', 1, NULL, 1, NOW(), 1, 0),
(1007, 102, '中', '2', 'warning', 2, NULL, 1, NOW(), 1, 0),
(1008, 102, '高', '3', 'danger', 3, NULL, 1, NOW(), 1, 0),
(1009, 103, '身份证', '1', NULL, 1, NULL, 1, NOW(), 1, 0),
(1010, 103, '护照', '2', NULL, 2, NULL, 1, NOW(), 1, 0),
(1011, 104, '系统通知', '1', 'blue', 1, NULL, 1, NOW(), 1, 0),
(1012, 104, '用户消息', '2', 'cyan', 2, NULL, 1, NOW(), 1, 0);

-- ============ 6. 系统参数 ============
INSERT INTO sys_config (id, config_group, name, config_key, config_value, built_in, remark, create_user, create_time, status, is_deleted) VALUES
(1000, 'system', '系统名称', 'system.name', 'ypbin 企业管理平台', 1, '系统显示名称', 1, NOW(), 1, 0),
(1001, 'system', '备案号', 'system.icp', '湘ICP备2026000011号', 1, '底部备案信息', 1, NOW(), 1, 0),
(1002, 'security', '登录失败锁定次数', 'security.login.fail-limit', '5', 1, '连续失败锁定', 1, NOW(), 1, 0),
(1003, 'security', '锁定时长(分钟)', 'security.login.lock-minutes', '30', 1, '锁定时间', 1, NOW(), 1, 0),
(1004, 'upload', '默认存储平台', 'upload.default-platform', 'local', 1, '文件上传平台', 1, NOW(), 1, 0),
(1005, 'ai', '默认模型', 'ai.default-model', 'deepseek-chat', 1, 'AI 默认模型', 1, NOW(), 1, 0),
(1006, 'ai', '温度参数', 'ai.temperature', '0.7', 1, '模型温度', 1, NOW(), 1, 0);

-- ============ 7. 开放平台应用（演示值，非真实密钥） ============
INSERT INTO sys_app (id, access_key, secret_key, app_name, expire_time, enabled, create_user, create_time, status, is_deleted) VALUES
(1000, 'demo-access-key-001', 'demo-secret-key-00000000000000000000000001', 'CRM 数据同步', DATE_ADD(NOW(), INTERVAL 365 DAY), 1, 1, NOW(), 1, 0),
(1001, 'demo-access-key-002', 'demo-secret-key-00000000000000000000000002', '移动端 App', DATE_ADD(NOW(), INTERVAL 180 DAY), 1, 1, NOW(), 1, 0),
(1002, 'demo-access-key-003', 'demo-secret-key-00000000000000000000000003', '数据分析平台', DATE_ADD(NOW(), INTERVAL 90 DAY), 1, 1, NOW(), 1, 0);

-- ============ 8. 定时任务 ============
INSERT INTO sys_job (id, name, executor, cron, fixed_rate_seconds, args, timeout_seconds, concurrent_guard, create_user, create_time, status, is_deleted) VALUES
(1000, '通知投递对账', 'noticeDeliveryReconcile', '0 0/5 * * * *', NULL, NULL, 60, 1, 1, NOW(), 1, 0),
(1001, '会话心跳清理', 'sessionHeartbeatClean', NULL, 300, NULL, 30, 1, 1, NOW(), 1, 0),
(1002, '数据备份提醒', 'dataBackupRemind', '0 0 2 * * ?', NULL, NULL, 30, 1, 1, NOW(), 1, 0),
(1003, '演示数据巡检', 'demoDataCheck', '0 0 1 * * ?', NULL, NULL, 30, 1, 1, NOW(), 0, 0);

-- 任务执行日志（造近 30 天历史）
INSERT INTO sys_job_log (id, job_id, job_name, executor, trigger_time, manual, outcome, duration_ms, error_msg, create_user, create_time, status, is_deleted) VALUES
(1000, 1000, '通知投递对账', 'noticeDeliveryReconcile', DATE_SUB(NOW(), INTERVAL 1 HOUR), 0, 1, 512, NULL, 1, NOW(), 1, 0),
(1001, 1000, '通知投递对账', 'noticeDeliveryReconcile', DATE_SUB(NOW(), INTERVAL 2 HOUR), 0, 2, 0, 'SMTP 连接超时', 1, NOW(), 1, 0),
(1002, 1000, '通知投递对账', 'noticeDeliveryReconcile', DATE_SUB(NOW(), INTERVAL 3 HOUR), 0, 1, 634, NULL, 1, NOW(), 1, 0),
(1003, 1001, '会话心跳清理', 'sessionHeartbeatClean', DATE_SUB(NOW(), INTERVAL 30 MINUTE), 0, 1, 45, NULL, 1, NOW(), 1, 0),
(1004, 1002, '数据备份提醒', 'dataBackupRemind', DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 1, 210, NULL, 1, NOW(), 1, 0),
(1005, 1000, '通知投递对账', 'noticeDeliveryReconcile', DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 1, 489, NULL, 1, NOW(), 1, 0),
(1006, 1001, '会话心跳清理', 'sessionHeartbeatClean', DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 1, 52, NULL, 1, NOW(), 1, 0),
(1007, 1002, '数据备份提醒', 'dataBackupRemind', DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 1, 198, NULL, 1, NOW(), 1, 0),
(1008, 1000, '通知投递对账', 'noticeDeliveryReconcile', DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 1, 456, NULL, 1, NOW(), 1, 0),
(1009, 1001, '会话心跳清理', 'sessionHeartbeatClean', DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 1, 61, NULL, 1, NOW(), 1, 0),
(1010, 1000, '通知投递对账', 'noticeDeliveryReconcile', DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 1, 523, NULL, 1, NOW(), 1, 0),
(1011, 1002, '数据备份提醒', 'dataBackupRemind', DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 1, 187, NULL, 1, NOW(), 1, 0),
(1012, 1000, '通知投递对账', 'noticeDeliveryReconcile', DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 1, 498, NULL, 1, NOW(), 1, 0),
(1013, 1001, '会话心跳清理', 'sessionHeartbeatClean', DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 1, 58, NULL, 1, NOW(), 1, 0);

-- ============ 9. 公告 ============
INSERT INTO sys_notice (id, tenant_id, title, content, cover, notice_type, notice_scope, scope_target_ids, notify_methods, is_top, publish_type, publish_status, publish_version, scheduled_time, publish_time, effective_time, expire_time, create_user, create_time, status, is_deleted) VALUES
(1000, 1, '关于 2026 年中秋节放假安排的通知', '<p>全体员工：</p><p>根据国家法定节假日安排，2026 年中秋节放假时间为 <strong>9 月 13 日至 9 月 15 日</strong>，共 3 天。9 月 16 日（周三）正常上班。</p><p>请各部门提前做好工作安排，确保放假期间系统稳定运行。</p><p>特此通知。</p><p style="text-align:right">行政部<br/>2026-08-28</p>', NULL, 1, 1, NULL, 'site', 1, 1, 2, 1, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(1001, 1, '系统 v1.4.1 版本发布公告', '<p>各位同事：</p><p>平台已完成 v1.4.1 版本升级，本次更新内容包括：</p><ul><li>Jackson 3 序列化体系全面迁移，时间格式与引用翻译恢复正常</li><li>AI 对话模块增强，新增用量统计</li><li>修复多处已知问题，提升稳定性</li></ul><p>如遇异常请及时反馈至运维组。</p><p style="text-align:right">平台运维组<br/>2026-08-26</p>', NULL, 2, 1, NULL, 'site,email', 0, 1, 2, 1, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 1, 0),
(1002, 1, '关于开展第三季度团建活动的通知', '<p>为增强团队凝聚力，公司定于 <strong>9 月 20 日</strong> 组织第三季度团建活动。</p><p>活动地点：长沙市橘子洲头；集合时间：上午 9:00。</p><p>请各部门统计参加人数，于 9 月 10 日前报至行政部。</p><p style="text-align:right">行政部<br/>2026-08-25</p>', NULL, 1, 1, NULL, 'site', 0, 1, 2, 1, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 4 DAY), 1, 0),
(1003, 1, '2026 年度员工体检安排', '<p>各位同事：</p><p>2026 年度员工健康体检将于 9 月起分批进行，请根据邮件通知的时间前往指定医院。</p><p>体检项目包含基础检查、血常规、肝功能、心电图等，请体检前保持空腹。</p><p style="text-align:right">行政部<br/>2026-08-20</p>', NULL, 1, 1, NULL, 'site,email', 0, 1, 2, 1, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 6 DAY), 1, 0),
(1004, 1, '信息系统等级保护测评配合通知', '<p>各部门：</p><p>平台将于本月接受信息安全等级保护（等保）测评，请各部门配合提供相关资料，并确保系统账号口令符合安全规范。</p><p>具体安排见附件邮件。</p><p style="text-align:right">运维组<br/>2026-08-18</p>', NULL, 2, 1, NULL, 'site', 1, 1, 2, 1, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 7 DAY), 1, 0),
(1005, 1, '【草稿】AI 能力开放申请流程（草稿）', '<p>待完善：AI 能力开放申请流程与审核标准。</p>', NULL, 2, 1, NULL, 'site', 0, 1, 0, 0, NULL, NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 0);

-- 公告投递记录
INSERT INTO sys_notice_delivery (id, tenant_id, notice_id, publish_version, receiver_user_id, channel, target_address, delivery_status, retry_count, next_retry_time, error_message, delivered_time, create_time, update_time) VALUES
(1000, 1, 1000, 1, 2, 'site', NULL, 'DELIVERED', 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1001, 1, 1000, 1, 3, 'site', NULL, 'DELIVERED', 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1002, 1, 1000, 1, 1000, 'site', NULL, 'DELIVERED', 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1003, 1, 1001, 1, 1001, 'site', NULL, 'DELIVERED', 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(1004, 1, 1001, 1, 1002, 'site', NULL, 'DELIVERED', 0, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

-- ============ 10. 站内消息 ============
INSERT INTO sys_message (id, tenant_id, notice_id, publish_version, receiver_user_id, title, content, message_type, read_status, create_user, create_time, status, is_deleted) VALUES
(1000, 1, 1000, 1, 2, '关于 2026 年中秋节放假安排的通知', '根据国家法定节假日安排，2026 年中秋节放假时间为 9 月 13 日至 9 月 15 日，共 3 天。', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(1001, 1, 1000, 1, 3, '关于 2026 年中秋节放假安排的通知', '根据国家法定节假日安排，2026 年中秋节放假时间为 9 月 13 日至 9 月 15 日，共 3 天。', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(1002, 1, 1000, 1, 1000, '关于 2026 年中秋节放假安排的通知', '根据国家法定节假日安排，2026 年中秋节放假时间为 9 月 13 日至 9 月 15 日，共 3 天。', 1, 0, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(1003, 1, 1001, 1, 1001, '系统 v1.4.1 版本发布公告', '平台已完成 v1.4.1 版本升级，详见公告详情。', 1, 0, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 1, 0),
(1004, 1, 1001, 1, 1002, '系统 v1.4.1 版本发布公告', '平台已完成 v1.4.1 版本升级，详见公告详情。', 1, 1, 1, DATE_SUB(NOW(), INTERVAL 3 DAY), 1, 0),
(1005, 1, NULL, NULL, 1000, '你的工单 #20260828001 已处理完成', '你提交的「数据库连接超时」工单已由运维组处理完成，请确认结果。', 2, 0, 1006, DATE_SUB(NOW(), INTERVAL 5 HOUR), 1, 0),
(1006, 1, NULL, NULL, 1000, '审批提醒：License 授权申请待处理', '有一笔新的 License 授权申请等待你审批，请及时处理。', 2, 0, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 0),
(1007, 1, NULL, NULL, 1001, '你的密码已于今日修改', '你的登录密码已于今日修改，如非本人操作请立即联系管理员。', 2, 1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(1008, 1, NULL, NULL, 1003, '代码评审通知：PR #452', '前端组件库 PR #452 有新的评审意见，请查看。', 2, 0, 1000, DATE_SUB(NOW(), INTERVAL 3 HOUR), 1, 0);

-- ============ 11. 操作日志（sys_log 无 create_user/status/is_deleted 字段） ============
INSERT INTO sys_log (id, description, module, request_method, request_uri, request_param, request_body, response_body, status_code, ip, location, browser, os, client_id, client_type, auth_type, operate_user_id, operate_time, time_taken, success, error_msg) VALUES
(1000, '用户登录', 'auth', 'POST', '/auth/login', '{"username":"lilei"}', NULL, NULL, 200, '113.240.32.15', '湖南长沙', 'Chrome 148', 'Windows 11', 'web', 'WEB', 'password', 2, DATE_SUB(NOW(), INTERVAL 2 HOUR), 156, 1, NULL),
(1001, '查询用户列表', 'system', 'GET', '/system/user/list', 'page=1&pageSize=10', NULL, NULL, 200, '113.240.32.15', '湖南长沙', 'Chrome 148', 'Windows 11', 'web', 'WEB', NULL, 2, DATE_SUB(NOW(), INTERVAL 2 HOUR), 23, 1, NULL),
(1002, '新增用户', 'system', 'POST', '/system/user', NULL, '{"username":"demo01","realName":"演示用户"}', NULL, 200, '113.240.32.15', '湖南长沙', 'Chrome 148', 'Windows 11', 'web', 'WEB', NULL, 2, DATE_SUB(NOW(), INTERVAL 3 HOUR), 45, 1, NULL),
(1003, '修改角色', 'system', 'PUT', '/system/role/100', NULL, '{"id":100,"name":"研发工程师"}', NULL, 200, '183.212.10.88', '江苏南京', 'Edge 148', 'Windows 11', 'web', 'WEB', NULL, 1000, DATE_SUB(NOW(), INTERVAL 5 HOUR), 32, 1, NULL),
(1004, '发布公告', 'notice', 'POST', '/system/notice/publish', NULL, '{"id":1000,"publishStatus":2}', NULL, 200, '183.212.10.88', '江苏南京', 'Edge 148', 'Windows 11', 'web', 'WEB', NULL, 1000, DATE_SUB(NOW(), INTERVAL 1 DAY), 67, 1, NULL),
(1005, '登录失败', 'auth', 'POST', '/auth/login', '{"username":"unknown"}', NULL, NULL, 200, '218.76.45.120', '湖南株洲', 'Chrome 147', 'Windows 10', 'web', 'WEB', 'password', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), 89, 0, '用户名或密码错误'),
(1006, '导出用户', 'system', 'GET', '/system/user/export', 'page=1&pageSize=1000', NULL, NULL, 200, '183.212.10.88', '江苏南京', 'Edge 148', 'Windows 11', 'web', 'WEB', NULL, 1000, DATE_SUB(NOW(), INTERVAL 2 DAY), 1240, 1, NULL),
(1007, '重置密码', 'system', 'PUT', '/system/user/1005/password', NULL, '{"userId":1005}', NULL, 200, '113.240.32.15', '湖南长沙', 'Chrome 148', 'Windows 11', 'web', 'WEB', NULL, 2, DATE_SUB(NOW(), INTERVAL 3 DAY), 78, 1, NULL),
(1008, 'AI 对话', 'ai', 'POST', '/ai/chat', NULL, '{"message":"介绍一下平台功能"}', NULL, 200, '183.212.10.88', '江苏南京', 'Chrome 148', 'macOS 15', 'web', 'WEB', NULL, 1007, DATE_SUB(NOW(), INTERVAL 4 HOUR), 3200, 1, NULL),
(1009, '更新系统参数', 'system', 'PUT', '/system/config/1000', NULL, '{"id":1000,"configValue":"ypbin 企业管理平台"}', NULL, 200, '113.240.32.15', '湖南长沙', 'Chrome 148', 'Windows 11', 'web', 'WEB', NULL, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), 41, 1, NULL),
(1010, '新增部门', 'system', 'POST', '/system/dept', NULL, '{"name":"行政部"}', NULL, 200, '183.212.10.88', '江苏南京', 'Edge 148', 'Windows 11', 'web', 'WEB', NULL, 1000, DATE_SUB(NOW(), INTERVAL 5 DAY), 36, 1, NULL),
(1011, '查询操作日志', 'monitor', 'GET', '/system/log/list', 'page=1&pageSize=10', NULL, NULL, 200, '113.240.32.15', '湖南长沙', 'Chrome 148', 'Windows 11', 'web', 'WEB', NULL, 2, DATE_SUB(NOW(), INTERVAL 1 HOUR), 18, 1, NULL);

-- ============ 12. 文件记录 ============
INSERT INTO sys_file (id, platform, bucket, url, original_name, file_name, path, size, content_type, extension, hash, upload_user_id, module, storage_status, error_message, create_user, create_time, status, is_deleted) VALUES
(1000, 'local', 'default', '/api/file/preview/1000', '产品需求说明书_v2.3.pdf', 'prod_req_v23.pdf', '2026/08/1000.pdf', 2456789, 'application/pdf', 'pdf', 'a3f8b2c1d4e5f6a7b8c9d0e1f2a3b4c5', 1000, 'notice', 'ACTIVE', NULL, 1000, DATE_SUB(NOW(), INTERVAL 3 DAY), 1, 0),
(1001, 'local', 'default', '/api/file/preview/1001', '2026Q3经营分析报表.xlsx', 'ops_report_q3.xlsx', '2026/08/1001.xlsx', 512340, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 'xlsx', 'b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6', 1014, 'report', 'ACTIVE', NULL, 1014, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(1002, 'local', 'default', '/api/file/preview/1002', '员工入职登记表_新员工.docx', 'onboard_form.docx', '2026/08/1002.docx', 89012, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'docx', 'c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7', 1011, 'file', 'ACTIVE', NULL, 1011, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 0),
(1003, 'local', 'default', '/api/file/preview/1003', '平台架构图_v1.4.png', 'arch_v14.png', '2026/08/1003.png', 2345678, 'image/png', 'png', 'd3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8', 1000, 'doc', 'ACTIVE', NULL, 1000, DATE_SUB(NOW(), INTERVAL 4 DAY), 1, 0),
(1004, 'local', 'default', '/api/file/preview/1004', '合同模板_商务合作.docx', 'contract_template.docx', '2026/08/1004.docx', 65432, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'docx', 'e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9', 1009, 'file', 'ACTIVE', NULL, 1009, DATE_SUB(NOW(), INTERVAL 5 DAY), 1, 0);

-- ============ 13. 租户（演示租户） ============
INSERT INTO sys_tenant (id, name, code, template_id, contact_name, contact_phone, expire_date, remark, create_user, create_time, status, is_deleted) VALUES
(100, '云启科技（演示租户）', 'yunqi', NULL, '陈总', '13800002001', DATE_ADD(CURDATE(), INTERVAL 300 DAY), '演示用租户，展示多租户隔离', 1, NOW(), 1, 0),
(101, '星辰网络（演示租户）', 'xingchen', NULL, '刘总', '13800002002', DATE_ADD(CURDATE(), INTERVAL 200 DAY), '演示用租户，展示多租户隔离', 1, NOW(), 1, 0);

-- ============ 14. 用户第三方绑定 ============
INSERT INTO sys_user_social (id, user_id, platform, open_id, union_id, nickname, avatar, access_token, create_user, create_time, status, is_deleted) VALUES
(1000, 1000, 'github', 'github_open_1000', 'union_1000', 'chenhaoran', NULL, 'gho_token_demo_1000', 1000, NOW(), 1, 0),
(1001, 1003, 'gitee', 'gitee_open_1003', 'union_1003', 'xuzewei', NULL, 'gte_token_demo_1003', 1003, NOW(), 1, 0),
(1002, 1007, 'wechat', 'wx_open_1007', 'union_1007', '倩倩', NULL, 'wx_token_demo_1007', 1007, NOW(), 1, 0);

-- ============ 15. License（演示授权记录） ============
INSERT INTO sys_license (id, license_id, subject, remark, fingerprints, tenant_id, effective_at, expire_at, grace_days, modules, quotas, attributes, delivery_mode, source, app_id, auth_code, approve_status, approve_user, approve_time, reject_reason, create_user, create_time, status, is_deleted) VALUES
(1000, 'YPBIN-2026-0001', '云启科技', '演示授权记录：企业版', NULL, 'yunqi', DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_ADD(NOW(), INTERVAL 300 DAY), 7, NULL, NULL, NULL, 'CODE', 'manual', 1000, NULL, 'ISSUED', 6, DATE_SUB(NOW(), INTERVAL 55 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 60 DAY), 1, 0),
(1001, 'YPBIN-2026-0002', '星辰网络', '演示授权记录：标准版', NULL, 'xingchen', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 200 DAY), 7, NULL, NULL, NULL, 'CODE', 'manual', 1001, NULL, 'ISSUED', 6, DATE_SUB(NOW(), INTERVAL 28 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 30 DAY), 1, 0),
(1002, NULL, '测试科技有限公司', '演示授权记录：审批中', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, 'CODE', 'manual', NULL, NULL, 'PENDING', NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 0);

-- ============ 16. AI 模型配置（api_key 为演示值） ============
INSERT INTO ai_model_config (id, tenant_id, name, provider, model_type, api_key, base_url, model_name, is_default, remark, create_user, create_time, status, is_deleted) VALUES
(100, 1, 'DeepSeek-V4 对话', 'deepseek', 'chat', 'demo-deepseek-api-key-0001', 'https://api.deepseek.com', 'deepseek-chat', 1, '默认对话模型', 1, NOW(), 1, 0),
(101, 1, 'DeepSeek-V4 推理', 'deepseek', 'chat', 'demo-deepseek-api-key-0002', 'https://api.deepseek.com', 'deepseek-reasoner', 0, '深度推理模型', 1, NOW(), 1, 0),
(102, 1, 'OpenAI GPT-5.6', 'openai', 'chat', 'demo-openai-api-key-0003', 'https://api.openai.com/v1', 'gpt-5.6', 0, '备用海外模型', 1, NOW(), 1, 0);

-- ============ 17. AI 知识库与文档 ============
INSERT INTO ai_knowledge_base (id, tenant_id, name, description, doc_count, icon, remark, widget_token, widget_enabled, share_token, share_enabled, share_expire_time, share_password, create_user, create_time, status, is_deleted) VALUES
(100, 1, '产品帮助中心', '平台使用帮助文档知识库，供 AI 助手检索回答', 2, 'book', '帮助文档', 'demo-widget-token-001', 1, 'demo-share-token-001', 1, DATE_ADD(NOW(), INTERVAL 90 DAY), NULL, 1, DATE_SUB(NOW(), INTERVAL 10 DAY), 1, 0),
(101, 1, '研发规范库', '团队研发规范与最佳实践，供代码助手检索', 1, 'code', '研发规范', NULL, 0, NULL, 0, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 8 DAY), 1, 0);

INSERT INTO ai_document (id, tenant_id, knowledge_base_id, filename, file_size, chunk_count, status, error_msg, file_path, source_type, source_url, create_user, create_time, is_deleted) VALUES
(100, 1, 100, '如何创建用户与分配角色.md', 2048, 3, 1, NULL, 'kb/2026/08/help-user-role.md', 'upload', NULL, 1, DATE_SUB(NOW(), INTERVAL 9 DAY), 0),
(101, 1, 100, '公告发布操作指南.md', 1536, 2, 1, NULL, 'kb/2026/08/help-notice.md', 'upload', NULL, 1, DATE_SUB(NOW(), INTERVAL 8 DAY), 0),
(102, 1, 101, '后端代码规范.md', 4096, 5, 1, NULL, 'kb/2026/08/dev-backend-style.md', 'upload', NULL, 1, DATE_SUB(NOW(), INTERVAL 7 DAY), 0);

INSERT INTO ai_document_chunk (id, tenant_id, knowledge_base_id, document_id, chunk_index, content, char_count, create_user, create_time, status, is_deleted) VALUES
(100, 1, 100, 100, 0, '创建用户：进入系统管理-用户管理，点击新增，填写登录账号、姓名、部门、角色后保存。', 40, 1, NOW(), 1, 0),
(101, 1, 100, 100, 1, '分配角色：在用户列表勾选用户，点击分配角色，选择角色后确认。', 28, 1, NOW(), 1, 0),
(102, 1, 100, 100, 2, '角色权限：角色关联菜单与数据范围，修改后即时生效。', 24, 1, NOW(), 1, 0),
(103, 1, 100, 101, 0, '发布公告：进入消息中心-公告管理，编写内容后选择发布方式（立即/定时）。', 34, 1, NOW(), 1, 0),
(104, 1, 100, 101, 1, '公告范围：支持全体、指定角色、指定部门、指定用户四种范围。', 28, 1, NOW(), 1, 0),
(105, 1, 101, 102, 0, '命名规范：类名 UpperCamelCase，方法 lowerCamelCase，常量全大写。', 30, 1, NOW(), 1, 0),
(106, 1, 101, 102, 1, '事务规范：写操作必须标注 @Transactional(rollbackFor = Exception.class)。', 34, 1, NOW(), 1, 0),
(107, 1, 101, 102, 2, '异常规范：业务异常统一 HTTP 200，通过 R.code 区分业务码。', 30, 1, NOW(), 1, 0);

-- ============ 18. AI 提示词模板 ============
INSERT INTO ai_prompt_template (id, tenant_id, name, category, template, description, create_user, create_time, status, is_deleted) VALUES
(100, 1, '周报生成', 'writing', '请根据以下工作内容生成一份周报，要求结构清晰、重点突出：\n{content}', '自动生成周报', 1, NOW(), 1, 0),
(101, 1, '会议纪要', 'writing', '请将以下会议记录整理成结构化纪要，包含议题、结论、待办事项：\n{content}', '会议纪要整理', 1, NOW(), 1, 0),
(102, 1, 'SQL 优化建议', 'coding', '请分析以下 SQL 的执行效率并给出优化建议：\n{content}', 'SQL 性能优化', 1, NOW(), 1, 0),
(103, 1, '代码 Review', 'coding', '请 Review 以下代码，指出问题并给出改进建议：\n{content}', '代码评审助手', 1, NOW(), 1, 0);

-- ============ 19. AI 对话会话与消息 ============
INSERT INTO ai_chat_session (id, tenant_id, user_id, title, role_id, model_id, context_window, total_tokens, message_count, is_pinned, last_message_at, create_time, update_time, create_user, update_user, status, is_deleted) VALUES
(100, 1, 1007, '平台功能咨询', 1, 100, 8, 1450, 6, 1, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), 1007, 1007, 1, 0),
(101, 1, 1000, '接口性能优化讨论', 3, 100, 8, 2380, 8, 0, DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 5 HOUR), 1000, 1000, 1, 0),
(102, 1, 1003, '前端组件改造方案', 3, 100, 8, 1890, 5, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 1003, 1003, 1, 0);

INSERT INTO ai_chat_message (id, session_id, tenant_id, user_id, parent_id, role, content, tokens, model_name, finish_reason, tool_calls, images, metadata, create_time, create_user, status, is_deleted) VALUES
(100, 100, 1, 1007, NULL, 'user', '你好，请介绍一下平台的主要功能', 12, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR), 1007, 1, 0),
(101, 100, 1, 1007, 100, 'assistant', '你好！这是一个企业级后台管理系统，主要功能包括：\n\n1. **组织权限**：用户、角色、部门、菜单的 RBAC 权限模型\n2. **多租户**：行级数据隔离与权限模板\n3. **消息中心**：站内信 SSE 实时推送\n4. **任务调度**：动态定时任务\n5. **AI 对话**：配置驱动的智能助手\n6. **商业授权**：License 授权管理\n\n你可以通过左侧菜单体验这些功能，有什么想深入了解的吗？', 128, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 HOUR), 1007, 1, 0),
(102, 100, 1, 1007, 101, 'user', 'AI 对话功能是怎么配置的？', 10, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), 1007, 1, 0),
(103, 100, 1, 1007, 102, 'assistant', 'AI 对话采用**模型配置表驱动**设计：\n\n- 在「AI 管理-模型配置」中添加模型（地址、密钥、型号）\n- 支持多模型动态切换，默认模型在参数配置中指定\n- 对话支持流式输出（SSE）与多轮记忆\n- 每次对话自动记录 Token 用量，可在用量统计中查看\n\n模型密钥在后台配置，不写死在 yml 文件中，更安全。', 110, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), 1007, 1, 0),
(104, 101, 1, 1000, NULL, 'user', '我们的列表接口响应慢，如何优化？', 12, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR), 1000, 1, 0),
(105, 101, 1, 1000, 104, 'assistant', '列表接口慢通常有几个方向：\n\n1. **N+1 查询**：检查循环内查库，批量收集 ID 一次查询\n2. **缓存**：热点数据加缓存（平台已内置多级缓存）\n3. **分页 SQL**：确认使用索引、避免大偏移量\n4. **序列化**：大对象关注 JSON 序列化耗时\n\n平台内置的 @RefText 引用翻译会自动预加载避免 N+1，建议先确认是否命中此场景。', 98, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR), 1000, 1, 0),
(106, 102, 1, 1003, NULL, 'user', 'Vue3 组件如何做性能优化？', 10, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), 1003, 1, 0),
(107, 102, 1, 1003, 106, 'assistant', 'Vue3 组件性能优化建议：\n\n- 使用 `computed` 缓存派生数据，避免重复计算\n- 列表项用 `v-memo` 或 `key` 优化 diff\n- 大数据量列表考虑虚拟滚动\n- 组件懒加载（`defineAsyncComponent`）\n- 避免不必要的响应式深度监听\n\n结合 Vben 框架，优先用 `useVbenForm`、`vxe-table` 的高效渲染。', 85, 'deepseek-chat', 'stop', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), 1003, 1, 0);

-- ============ 20. AI 用量日志 ============
INSERT INTO ai_usage_log (id, tenant_id, user_id, conversation_id, model_id, model_name, input_tokens, output_tokens, total_tokens, latency_ms, create_user, create_time, status, is_deleted) VALUES
(100, 1, 1007, 100, 100, 'deepseek-chat', 512, 128, 640, 1240, 1007, DATE_SUB(NOW(), INTERVAL 3 HOUR), 1, 0),
(101, 1, 1007, 100, 100, 'deepseek-chat', 380, 110, 490, 980, 1007, DATE_SUB(NOW(), INTERVAL 2 HOUR), 1, 0),
(102, 1, 1000, 101, 100, 'deepseek-chat', 720, 98, 818, 1520, 1000, DATE_SUB(NOW(), INTERVAL 6 HOUR), 1, 0),
(103, 1, 1000, 101, 100, 'deepseek-chat', 850, 132, 982, 1680, 1000, DATE_SUB(NOW(), INTERVAL 5 HOUR), 1, 0),
(104, 1, 1003, 102, 100, 'deepseek-chat', 420, 85, 505, 890, 1003, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 0),
(105, 1, 1007, NULL, 100, 'deepseek-chat', 210, 45, 255, 560, 1007, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(106, 1, 1000, NULL, 100, 'deepseek-chat', 660, 118, 778, 1430, 1000, DATE_SUB(NOW(), INTERVAL 3 DAY), 1, 0),
(107, 1, 1007, NULL, 101, 'deepseek-reasoner', 980, 256, 1236, 4100, 1007, DATE_SUB(NOW(), INTERVAL 4 DAY), 1, 0);

-- ============ 21. AI 查询日志（知识库检索） ============
INSERT INTO ai_query_log (id, tenant_id, knowledge_base_id, query, source, create_user, create_time, status, is_deleted) VALUES
(100, 1, 100, '如何创建用户并分配角色', 'ai_chat', 1007, DATE_SUB(NOW(), INTERVAL 3 HOUR), 1, 0),
(101, 1, 100, '公告怎么定时发布', 'ai_chat', 1007, DATE_SUB(NOW(), INTERVAL 2 DAY), 1, 0),
(102, 1, 101, '事务注解怎么写', 'ai_chat', 1000, DATE_SUB(NOW(), INTERVAL 1 DAY), 1, 0),
(103, 1, 101, '代码命名规范', 'ai_chat', 1000, DATE_SUB(NOW(), INTERVAL 4 DAY), 1, 0);

-- ============ 22. AI 角色（补充演示角色 + 收藏） ============
INSERT INTO ai_chat_role (id, tenant_id, name, description, avatar, system_prompt, category, model_preference, temperature, is_builtin, sort, create_time, create_user, status, is_deleted) VALUES
(100, 1, '企业顾问', '擅长企业管理咨询、制度设计与流程优化', NULL, '你是一位资深企业管理顾问，擅长组织架构、流程优化与制度建设。回答时结合中国企业实际，给出可落地的建议。', 'business', 'deepseek-chat', 0.60, 0, 10, DATE_SUB(NOW(), INTERVAL 5 DAY), 1, 1, 0),
(101, 1, '营销文案专家', '擅长撰写营销文案、产品介绍与推广方案', NULL, '你是一位资深营销文案专家，擅长撰写吸引人的营销文案、产品介绍和推广方案。文案要求：1) 抓住卖点 2) 语言生动 3) 适合目标受众 4) 有明确的行动号召。', 'marketing', 'deepseek-chat', 0.80, 0, 11, DATE_SUB(NOW(), INTERVAL 5 DAY), 1, 1, 0);

INSERT INTO ai_chat_role_favorite (id, tenant_id, user_id, role_id, create_time, create_user, status, is_deleted) VALUES
(100, 1, 1007, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), 1007, 1, 0),
(101, 1, 1007, 100, DATE_SUB(NOW(), INTERVAL 3 DAY), 1007, 1, 0),
(102, 1, 1000, 3, DATE_SUB(NOW(), INTERVAL 2 DAY), 1000, 1, 0);

-- ============ 完成提示 ============
SELECT '✅ 演示数据已导入：15 个部门、15 个用户、6 个角色、5 类字典、6 条公告、9 条消息、4 个任务、14 条任务日志、12 条操作日志、5 个文件、2 个租户、3 个开放应用、3 个 AI 模型、2 个知识库、3 个文档、4 个提示词、3 个会话、8 条对话消息、8 条用量日志、4 条查询日志、6 个 AI 角色' AS result;
