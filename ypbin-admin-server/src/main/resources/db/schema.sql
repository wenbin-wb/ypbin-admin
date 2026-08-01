-- =============================================================
-- ypbin-admin 全量建表脚本（汇总参考，非 Flyway 迁移）
-- 
-- 用途：快速浏览完整库结构 / 离线一次性建库 / DBA 交接。
-- 权威来源：本文件由实跑通过的库 SHOW CREATE TABLE 汇总导出，反映迁移执行后的最终结构。
-- 注意：实际建库以 db/migration 下的 Flyway 脚本（V1~V14）为准；本文件不参与 Flyway 执行，
--       不要放入 migration 目录。种子数据（超管/菜单/字典等）见 Flyway 的 V2/V3/V4，本文件仅含表结构。
-- =============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '租户名称',
  `code` varchar(64) NOT NULL COMMENT '租户编码',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `expire_date` date DEFAULT NULL COMMENT '到期时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户';

DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户 ID',
  `pid` bigint NOT NULL DEFAULT '0' COMMENT '父部门 ID',
  `name` varchar(64) NOT NULL COMMENT '部门名称',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `leader` varchar(64) DEFAULT NULL COMMENT '负责人',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统部门';

DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户 ID',
  `name` varchar(64) NOT NULL COMMENT '岗位名称',
  `code` varchar(64) NOT NULL COMMENT '岗位编码',
  `category` varchar(32) DEFAULT NULL COMMENT '岗位分类',
  `sort` int DEFAULT '0' COMMENT '排序',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位';

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户 ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password` varchar(100) NOT NULL COMMENT '登录密码（BCrypt）',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `dept_id` bigint DEFAULT NULL COMMENT '部门 ID',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `gender` tinyint DEFAULT NULL COMMENT '性别：0 未知 1 男 2 女',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_reset_time` datetime DEFAULT NULL COMMENT '最后改密时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户 ID',
  `name` varchar(64) NOT NULL COMMENT '角色名称',
  `code` varchar(64) NOT NULL COMMENT '角色标识',
  `data_scope` tinyint DEFAULT '1' COMMENT '数据范围：1 全部 2 本部门及以下 3 本部门 4 仅本人 5 自定义',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色';

DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL COMMENT '主键',
  `pid` bigint NOT NULL DEFAULT '0' COMMENT '父菜单 ID',
  `name` varchar(64) NOT NULL COMMENT '菜单名称（路由 name）',
  `type` varchar(16) NOT NULL COMMENT '类型：catalog/menu/button/embedded/link',
  `path` varchar(255) DEFAULT NULL COMMENT '路由路径',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `auth_code` varchar(128) DEFAULT NULL COMMENT '权限标识',
  `redirect` varchar(255) DEFAULT NULL COMMENT '重定向',
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `icon` varchar(128) DEFAULT NULL COMMENT '图标',
  `active_icon` varchar(128) DEFAULT NULL COMMENT '激活图标',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `keep_alive` tinyint DEFAULT NULL COMMENT '是否缓存：1 是 0 否',
  `hide_in_menu` tinyint DEFAULT NULL COMMENT '是否隐藏：1 是 0 否',
  `iframe_src` varchar(255) DEFAULT NULL COMMENT '内嵌地址',
  `link` varchar(255) DEFAULT NULL COMMENT '外链地址',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统菜单';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联';

DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `post_id` bigint NOT NULL COMMENT '岗位 ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-岗位关联';

DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `menu_id` bigint NOT NULL COMMENT '菜单 ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单关联';

DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `dept_id` bigint NOT NULL COMMENT '部门 ID',
  PRIMARY KEY (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-部门关联';

DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '字典名称',
  `code` varchar(64) NOT NULL COMMENT '字典编码（字典类型）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典类型';

DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL COMMENT '主键',
  `dict_id` bigint NOT NULL COMMENT '所属字典 ID',
  `label` varchar(64) NOT NULL COMMENT '字典项标签',
  `value` varchar(64) NOT NULL COMMENT '字典项值',
  `color` varchar(32) DEFAULT NULL COMMENT '展示颜色',
  `sort` int DEFAULT '0' COMMENT '显示排序',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  KEY `idx_dict_id` (`dict_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据字典项';

DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL COMMENT '主键',
  `config_group` varchar(64) NOT NULL COMMENT '参数分组',
  `name` varchar(128) NOT NULL COMMENT '参数名称',
  `config_key` varchar(128) NOT NULL COMMENT '参数键',
  `config_value` varchar(512) DEFAULT NULL COMMENT '参数值',
  `built_in` tinyint NOT NULL DEFAULT '0' COMMENT '是否内置：1 是 0 否',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统参数';

DROP TABLE IF EXISTS `sys_user_password_history`;
CREATE TABLE `sys_user_password_history` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `password` varchar(100) NOT NULL COMMENT '历史密码（BCrypt）',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常 0 禁用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户历史密码';

DROP TABLE IF EXISTS `sys_user_social`;
CREATE TABLE `sys_user_social` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `platform` varchar(32) NOT NULL COMMENT '平台标识',
  `open_id` varchar(128) NOT NULL COMMENT '第三方 openId',
  `union_id` varchar(128) DEFAULT NULL COMMENT '第三方 unionId',
  `nickname` varchar(64) DEFAULT NULL COMMENT '第三方昵称',
  `avatar` varchar(512) DEFAULT NULL COMMENT '第三方头像',
  `access_token` varchar(512) DEFAULT NULL COMMENT '第三方 accessToken',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_open` (`platform`,`open_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-第三方平台绑定';

DROP TABLE IF EXISTS `sys_client`;
CREATE TABLE `sys_client` (
  `id` bigint NOT NULL COMMENT '主键',
  `client_id` varchar(64) NOT NULL COMMENT '客户端 ID',
  `client_secret` varchar(255) DEFAULT NULL COMMENT '客户端密钥',
  `client_type` varchar(16) DEFAULT NULL COMMENT '客户端类型',
  `auth_types` varchar(128) DEFAULT NULL COMMENT '认证方式，逗号分隔',
  `timeout` bigint DEFAULT NULL COMMENT 'Token 有效期（秒）',
  `active_timeout` bigint DEFAULT NULL COMMENT 'Token 活跃超时（秒）',
  `concurrent_enabled` tinyint DEFAULT NULL COMMENT '是否允许多端登录',
  `max_login_count` int DEFAULT NULL COMMENT '最大登录数，-1 不限制',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_client_id` (`client_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录客户端';

DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
  `id` bigint NOT NULL COMMENT '主键',
  `description` varchar(255) DEFAULT NULL COMMENT '日志描述',
  `module` varchar(64) DEFAULT NULL COMMENT '所属模块',
  `request_method` varchar(10) DEFAULT NULL COMMENT '请求方法',
  `request_uri` varchar(255) DEFAULT NULL COMMENT '请求 URI',
  `request_param` text COMMENT '请求参数',
  `request_body` mediumtext COMMENT '请求体',
  `response_body` mediumtext COMMENT '响应体',
  `status_code` int DEFAULT NULL COMMENT 'HTTP 状态码',
  `ip` varchar(64) DEFAULT NULL COMMENT '客户端 IP',
  `location` varchar(128) DEFAULT NULL COMMENT 'IP 归属地',
  `browser` varchar(128) DEFAULT NULL COMMENT '浏览器',
  `os` varchar(128) DEFAULT NULL COMMENT '操作系统',
  `client_id` varchar(64) DEFAULT NULL COMMENT '登录客户端 ID',
  `client_type` varchar(16) DEFAULT NULL COMMENT '客户端类型',
  `auth_type` varchar(16) DEFAULT NULL COMMENT '认证方式',
  `operate_user_id` bigint DEFAULT NULL COMMENT '操作人用户 ID',
  `operate_time` datetime DEFAULT NULL COMMENT '操作时间',
  `time_taken` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
  `success` tinyint DEFAULT NULL COMMENT '是否成功：1 是 0 否',
  `error_msg` text COMMENT '错误信息',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module`),
  KEY `idx_operate_user_id` (`operate_user_id`),
  KEY `idx_operate_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统日志';

DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file` (
  `id` bigint NOT NULL COMMENT '主键',
  `platform` varchar(32) DEFAULT NULL COMMENT '存储平台',
  `url` varchar(512) DEFAULT NULL COMMENT '文件 URL',
  `original_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `file_name` varchar(255) DEFAULT NULL COMMENT '存储文件名',
  `file_path` varchar(512) DEFAULT NULL COMMENT '文件路径',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME 类型',
  `extension` varchar(32) DEFAULT NULL COMMENT '文件扩展名',
  `hash` varchar(128) DEFAULT NULL COMMENT '文件哈希',
  `upload_user_id` bigint DEFAULT NULL COMMENT '上传人',
  `module` varchar(32) DEFAULT NULL COMMENT '所属业务模块',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件管理';

DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `content` text COMMENT '公告内容',
  `notice_type` tinyint DEFAULT NULL COMMENT '类型：1 通知 2 公告',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统公告';

DROP TABLE IF EXISTS `sys_message`;
CREATE TABLE `sys_message` (
  `id` bigint NOT NULL COMMENT '主键',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户 ID',
  `receiver_user_id` bigint NOT NULL COMMENT '接收人用户 ID',
  `title` varchar(255) NOT NULL COMMENT '消息标题',
  `content` text COMMENT '消息内容',
  `message_type` tinyint DEFAULT NULL COMMENT '类型：1 系统通知 2 用户消息',
  `read_status` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：0 未读 1 已读',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_receiver` (`receiver_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户消息';

DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '任务名称',
  `executor` varchar(64) NOT NULL COMMENT '执行器名称',
  `cron` varchar(64) DEFAULT NULL COMMENT 'cron 表达式',
  `fixed_rate_seconds` bigint DEFAULT NULL COMMENT '固定间隔秒数',
  `args` varchar(512) DEFAULT NULL COMMENT '执行参数',
  `timeout_seconds` bigint DEFAULT '0' COMMENT '执行超时秒数',
  `concurrent_guard` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用集群防重',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：1 启用 0 停用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_executor` (`executor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务';

DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log` (
  `id` bigint NOT NULL COMMENT '主键',
  `job_id` bigint NOT NULL COMMENT '任务 ID',
  `job_name` varchar(128) DEFAULT NULL COMMENT '任务名称',
  `executor` varchar(64) DEFAULT NULL COMMENT '执行器名称',
  `trigger_time` datetime DEFAULT NULL COMMENT '触发时间',
  `manual` tinyint DEFAULT NULL COMMENT '是否手动触发',
  `outcome` tinyint DEFAULT NULL COMMENT '结果：0 跳过 1 成功 2 失败',
  `duration_ms` bigint DEFAULT NULL COMMENT '执行耗时（毫秒）',
  `error_msg` text COMMENT '错误信息',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_job_id` (`job_id`),
  KEY `idx_trigger_time` (`trigger_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定时任务执行日志';

DROP TABLE IF EXISTS `sys_app`;
CREATE TABLE `sys_app` (
  `id` bigint NOT NULL COMMENT '主键',
  `access_key` varchar(64) NOT NULL COMMENT 'Access Key',
  `secret_key` varchar(255) NOT NULL COMMENT 'Secret Key',
  `app_name` varchar(128) DEFAULT NULL COMMENT '应用名称',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_user` bigint DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_user` bigint DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_access_key` (`access_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='开放平台应用';

