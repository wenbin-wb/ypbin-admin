-- =============================================================
-- ypbin-admin 建表脚本（开发阶段整合版，含全部表最终结构）
-- 约定：BaseEntity 公共列 id / create_user / create_time / update_user / update_time / status / is_deleted
--       TenantBaseEntity 额外含 tenant_id
-- =============================================================

-- 用户表
CREATE TABLE sys_user
(
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NULL COMMENT '租户 ID',
    username        VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password        VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt）',
    real_name       VARCHAR(64)  NULL COMMENT '真实姓名',
    nickname        VARCHAR(64)  NULL COMMENT '昵称',
    dept_id         BIGINT       NULL COMMENT '部门 ID',
    avatar          VARCHAR(255) NULL COMMENT '头像',
    phone           VARCHAR(20)  NULL COMMENT '手机号',
    email           VARCHAR(128) NULL COMMENT '邮箱',
    gender          TINYINT      NULL COMMENT '性别：0 未知 1 男 2 女',
    remark          VARCHAR(255) NULL COMMENT '备注',
    last_login_time DATETIME     NULL COMMENT '最后登录时间',
    pwd_reset_time  DATETIME     NULL COMMENT '最后改密时间',
    create_user     BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_user     BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NULL COMMENT '更新时间',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统用户';

-- 角色表
CREATE TABLE sys_role
(
    id          BIGINT      NOT NULL COMMENT '主键',
    tenant_id   BIGINT      NULL COMMENT '租户 ID',
    name        VARCHAR(64) NOT NULL COMMENT '角色名称',
    code        VARCHAR(64) NOT NULL COMMENT '角色标识',
    data_scope  TINYINT     NULL DEFAULT 1 COMMENT '数据范围：1 全部 2 本部门及以下 3 本部门 4 仅本人 5 自定义',
    sort        INT         NULL DEFAULT 0 COMMENT '显示排序',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT      NULL COMMENT '创建人',
    create_time DATETIME    NULL COMMENT '创建时间',
    update_user BIGINT      NULL COMMENT '更新人',
    update_time DATETIME    NULL COMMENT '更新时间',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统角色';

-- 部门表
CREATE TABLE sys_dept
(
    id          BIGINT      NOT NULL COMMENT '主键',
    tenant_id   BIGINT      NULL COMMENT '租户 ID',
    pid         BIGINT      NOT NULL DEFAULT 0 COMMENT '父部门 ID',
    name        VARCHAR(64) NOT NULL COMMENT '部门名称',
    sort        INT         NULL DEFAULT 0 COMMENT '显示排序',
    leader      VARCHAR(64) NULL COMMENT '负责人',
    phone       VARCHAR(20) NULL COMMENT '联系电话',
    email       VARCHAR(128) NULL COMMENT '邮箱',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT      NULL COMMENT '创建人',
    create_time DATETIME    NULL COMMENT '创建时间',
    update_user BIGINT      NULL COMMENT '更新人',
    update_time DATETIME    NULL COMMENT '更新时间',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统部门';

-- 菜单表（全局，不隔离租户）
CREATE TABLE sys_menu
(
    id           BIGINT       NOT NULL COMMENT '主键',
    pid          BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单 ID',
    name         VARCHAR(64)  NOT NULL COMMENT '菜单名称（路由 name）',
    type         VARCHAR(16)  NOT NULL COMMENT '类型：catalog/menu/button/embedded/link',
    path         VARCHAR(255) NULL COMMENT '路由路径',
    component    VARCHAR(255) NULL COMMENT '组件路径',
    auth_code    VARCHAR(128) NULL COMMENT '权限标识',
    redirect     VARCHAR(255) NULL COMMENT '重定向',
    title        VARCHAR(64)  NULL COMMENT '标题',
    icon         VARCHAR(128) NULL COMMENT '图标',
    active_icon  VARCHAR(128) NULL COMMENT '激活图标',
    sort         INT          NULL DEFAULT 0 COMMENT '显示排序',
    keep_alive   TINYINT      NULL COMMENT '是否缓存：1 是 0 否',
    hide_in_menu TINYINT      NULL COMMENT '是否隐藏：1 是 0 否',
    iframe_src   VARCHAR(255) NULL COMMENT '内嵌地址',
    link         VARCHAR(255) NULL COMMENT '外链地址',
    active_path           VARCHAR(255) NULL COMMENT '高亮的菜单路径',
    affix_tab             TINYINT      NULL COMMENT '是否固定标签页：1 是 0 否',
    badge                 VARCHAR(64)  NULL COMMENT '徽标内容',
    badge_type            VARCHAR(16)  NULL COMMENT '徽标类型：dot 点 normal 文字',
    badge_variants        VARCHAR(16)  NULL COMMENT '徽标样式',
    hide_children_in_menu TINYINT      NULL COMMENT '是否隐藏子菜单：1 是 0 否',
    hide_in_breadcrumb    TINYINT      NULL COMMENT '是否在面包屑中隐藏：1 是 0 否',
    hide_in_tab           TINYINT      NULL COMMENT '是否在标签栏中隐藏：1 是 0 否',
    create_user  BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NULL COMMENT '创建时间',
    update_user  BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NULL COMMENT '更新时间',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统菜单';

-- 租户表（全局，不隔离租户）
CREATE TABLE sys_tenant
(
    id            BIGINT      NOT NULL COMMENT '主键',
    name          VARCHAR(64) NOT NULL COMMENT '租户名称',
    code          VARCHAR(64) NOT NULL COMMENT '租户编码',
    template_id   BIGINT      NULL COMMENT '所属权限模板 ID',
    contact_name  VARCHAR(64) NULL COMMENT '联系人',
    contact_phone VARCHAR(20) NULL COMMENT '联系电话',
    expire_date   DATE        NULL COMMENT '到期时间',
    remark        VARCHAR(255) NULL COMMENT '备注',
    create_user   BIGINT      NULL COMMENT '创建人',
    create_time   DATETIME    NULL COMMENT '创建时间',
    update_user   BIGINT      NULL COMMENT '更新人',
    update_time   DATETIME    NULL COMMENT '更新时间',
    status        TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '租户';

-- 用户-角色关联
CREATE TABLE sys_user_role
(
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户-角色关联';

-- 角色-菜单关联
CREATE TABLE sys_role_menu
(
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    menu_id BIGINT NOT NULL COMMENT '菜单 ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色-菜单关联';

-- 角色-部门关联（数据权限自定义范围用）
CREATE TABLE sys_role_dept
(
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    dept_id BIGINT NOT NULL COMMENT '部门 ID',
    PRIMARY KEY (role_id, dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色-部门关联';

-- 数据字典类型
CREATE TABLE sys_dict
(
    id          BIGINT      NOT NULL COMMENT '主键',
    name        VARCHAR(64) NOT NULL COMMENT '字典名称',
    code        VARCHAR(64) NOT NULL COMMENT '字典编码（字典类型）',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT      NULL COMMENT '创建人',
    create_time DATETIME    NULL COMMENT '创建时间',
    update_user BIGINT      NULL COMMENT '更新人',
    update_time DATETIME    NULL COMMENT '更新时间',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '数据字典类型';

-- 数据字典项
CREATE TABLE sys_dict_item
(
    id          BIGINT      NOT NULL COMMENT '主键',
    dict_id     BIGINT      NOT NULL COMMENT '所属字典 ID',
    label       VARCHAR(64) NOT NULL COMMENT '字典项标签',
    value       VARCHAR(64) NOT NULL COMMENT '字典项值',
    color       VARCHAR(32) NULL COMMENT '展示颜色',
    sort        INT         NULL DEFAULT 0 COMMENT '显示排序',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT      NULL COMMENT '创建人',
    create_time DATETIME    NULL COMMENT '创建时间',
    update_user BIGINT      NULL COMMENT '更新人',
    update_time DATETIME    NULL COMMENT '更新时间',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_dict_id (dict_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '数据字典项';

-- 系统参数配置
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

-- 用户历史密码
CREATE TABLE sys_user_password_history
(
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    password    VARCHAR(100) NOT NULL COMMENT '历史密码（BCrypt）',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户历史密码';

-- 系统日志（操作日志 + 登录日志）
CREATE TABLE sys_log
(
    id              BIGINT       NOT NULL COMMENT '主键',
    description     VARCHAR(255) NULL COMMENT '日志描述',
    module          VARCHAR(64)  NULL COMMENT '所属模块',
    request_method  VARCHAR(10)  NULL COMMENT '请求方法',
    request_uri     VARCHAR(255) NULL COMMENT '请求 URI',
    request_param   TEXT         NULL COMMENT '请求参数',
    request_body    MEDIUMTEXT   NULL COMMENT '请求体',
    response_body   MEDIUMTEXT   NULL COMMENT '响应体',
    status_code     INT          NULL COMMENT 'HTTP 状态码',
    ip              VARCHAR(64)  NULL COMMENT '客户端 IP',
    location        VARCHAR(128) NULL COMMENT 'IP 归属地',
    browser         VARCHAR(128) NULL COMMENT '浏览器',
    os              VARCHAR(128) NULL COMMENT '操作系统',
    client_id       VARCHAR(64)  NULL COMMENT '登录客户端 ID',
    client_type     VARCHAR(16)  NULL COMMENT '客户端类型',
    auth_type       VARCHAR(16)  NULL COMMENT '认证方式',
    operate_user_id BIGINT       NULL COMMENT '操作人用户 ID',
    operate_time    DATETIME     NULL COMMENT '操作时间',
    time_taken      BIGINT       NULL COMMENT '耗时（毫秒）',
    success         TINYINT      NULL COMMENT '是否成功：1 是 0 否',
    error_msg       TEXT         NULL COMMENT '错误信息',
    PRIMARY KEY (id),
    KEY idx_module (module),
    KEY idx_operate_user_id (operate_user_id),
    KEY idx_operate_time (operate_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统日志';

-- 登录客户端
CREATE TABLE sys_client
(
    id                BIGINT      NOT NULL COMMENT '主键',
    client_id         VARCHAR(64) NOT NULL COMMENT '客户端 ID',
    client_secret     VARCHAR(255) NULL COMMENT '客户端密钥',
    client_type       VARCHAR(16) NULL COMMENT '客户端类型',
    auth_types        VARCHAR(128) NULL COMMENT '认证方式，逗号分隔',
    timeout           BIGINT      NULL COMMENT 'Token 有效期（秒）',
    active_timeout    BIGINT      NULL COMMENT 'Token 活跃超时（秒）',
    concurrent_enabled TINYINT    NULL COMMENT '是否允许多端登录',
    max_login_count   INT         NULL COMMENT '最大登录数，-1 不限制',
    remark            VARCHAR(255) NULL COMMENT '备注',
    create_user       BIGINT      NULL COMMENT '创建人',
    create_time       DATETIME    NULL COMMENT '创建时间',
    update_user       BIGINT      NULL COMMENT '更新人',
    update_time       DATETIME    NULL COMMENT '更新时间',
    status            TINYINT     NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '登录客户端';

-- 岗位
CREATE TABLE sys_post
(
    id          BIGINT      NOT NULL COMMENT '主键',
    tenant_id   BIGINT      NULL COMMENT '租户 ID',
    name        VARCHAR(64) NOT NULL COMMENT '岗位名称',
    code        VARCHAR(64) NOT NULL COMMENT '岗位编码',
    category    VARCHAR(32) NULL COMMENT '岗位分类',
    sort        INT         NULL DEFAULT 0 COMMENT '排序',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT      NULL COMMENT '创建人',
    create_time DATETIME    NULL COMMENT '创建时间',
    update_user BIGINT      NULL COMMENT '更新人',
    update_time DATETIME    NULL COMMENT '更新时间',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '岗位';

-- 用户-岗位关联
CREATE TABLE sys_user_post
(
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    post_id BIGINT NOT NULL COMMENT '岗位 ID',
    PRIMARY KEY (user_id, post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户-岗位关联';

-- 文件管理
CREATE TABLE sys_file
(
    id            BIGINT       NOT NULL COMMENT '主键',
    platform      VARCHAR(32)  NULL COMMENT '存储平台',
    url           VARCHAR(512) NULL COMMENT '文件 URL',
    original_name VARCHAR(255) NULL COMMENT '原始文件名',
    file_name     VARCHAR(255) NULL COMMENT '存储文件名',
    file_path     VARCHAR(512) NULL COMMENT '文件路径',
    file_size     BIGINT       NULL COMMENT '文件大小（字节）',
    content_type  VARCHAR(128) NULL COMMENT 'MIME 类型',
    extension     VARCHAR(32)  NULL COMMENT '文件扩展名',
    hash          VARCHAR(128) NULL COMMENT '文件哈希',
    upload_user_id BIGINT      NULL COMMENT '上传人',
    module        VARCHAR(32)  NULL COMMENT '所属业务模块',
    create_user   BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NULL COMMENT '创建时间',
    update_user   BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NULL COMMENT '更新时间',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文件管理';

-- 系统公告
CREATE TABLE sys_notice
(
    id              BIGINT       NOT NULL COMMENT '主键',
    title           VARCHAR(255) NOT NULL COMMENT '标题',
    content         MEDIUMTEXT   NULL COMMENT '公告内容（富文本）',
    cover           VARCHAR(512) NULL COMMENT '封面图 URL',
    notice_type     TINYINT      NULL COMMENT '类型：1 通知 2 公告',
    notice_scope    TINYINT      NULL DEFAULT 1 COMMENT '通知范围：1 全体 2 指定角色 3 指定部门 4 指定用户',
    scope_target_ids VARCHAR(1024) NULL COMMENT '范围目标 ID 集合（逗号分隔）',
    notify_methods  VARCHAR(64)  NULL COMMENT '通知方式（逗号分隔：site/email/sms）',
    is_top          TINYINT      NULL DEFAULT 0 COMMENT '是否置顶：1 是 0 否',
    publish_type    TINYINT      NULL DEFAULT 1 COMMENT '发布方式：1 立即 2 定时',
    publish_status  TINYINT      NULL DEFAULT 2 COMMENT '发布状态：0 草稿 1 待发布 2 已发布 3 已撤回',
    scheduled_time  DATETIME     NULL COMMENT '定时发布时间',
    publish_time    DATETIME     NULL COMMENT '实际发布时间',
    effective_time  DATETIME     NULL COMMENT '生效时间',
    expire_time     DATETIME     NULL COMMENT '失效时间',
    create_user     BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_user     BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NULL COMMENT '更新时间',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统公告';

-- 用户消息（站内信）
CREATE TABLE sys_message
(
    id               BIGINT       NOT NULL COMMENT '主键',
    tenant_id        BIGINT       NULL COMMENT '租户 ID',
    receiver_user_id BIGINT       NOT NULL COMMENT '接收人用户 ID',
    title            VARCHAR(255) NOT NULL COMMENT '消息标题',
    content          TEXT         NULL COMMENT '消息内容',
    message_type     TINYINT      NULL COMMENT '类型：1 系统通知 2 用户消息',
    read_status      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 1 已读',
    create_user      BIGINT       NULL COMMENT '创建人',
    create_time      DATETIME     NULL COMMENT '创建时间',
    update_user      BIGINT       NULL COMMENT '更新人',
    update_time      DATETIME     NULL COMMENT '更新时间',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_receiver (receiver_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户消息';

-- 定时任务
CREATE TABLE sys_job
(
    id                BIGINT       NOT NULL COMMENT '主键',
    name              VARCHAR(128) NOT NULL COMMENT '任务名称',
    executor          VARCHAR(64)  NOT NULL COMMENT '执行器名称',
    cron              VARCHAR(64)  NULL COMMENT 'cron 表达式',
    fixed_rate_seconds BIGINT      NULL COMMENT '固定间隔秒数',
    args              VARCHAR(512) NULL COMMENT '执行参数',
    timeout_seconds   BIGINT       NULL DEFAULT 0 COMMENT '执行超时秒数',
    concurrent_guard  TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用集群防重',
    create_user       BIGINT       NULL COMMENT '创建人',
    create_time       DATETIME     NULL COMMENT '创建时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：1 启用 0 停用',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_executor (executor)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '定时任务';

-- 定时任务执行日志
CREATE TABLE sys_job_log
(
    id           BIGINT       NOT NULL COMMENT '主键',
    job_id       BIGINT       NOT NULL COMMENT '任务 ID',
    job_name     VARCHAR(128) NULL COMMENT '任务名称',
    executor     VARCHAR(64)  NULL COMMENT '执行器名称',
    trigger_time DATETIME     NULL COMMENT '触发时间',
    manual       TINYINT      NULL COMMENT '是否手动触发',
    outcome      TINYINT      NULL COMMENT '结果：0 跳过 1 成功 2 失败',
    duration_ms  BIGINT       NULL COMMENT '执行耗时（毫秒）',
    error_msg    TEXT         NULL COMMENT '错误信息',
    create_user  BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NULL COMMENT '创建时间',
    update_user  BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NULL COMMENT '更新时间',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_job_id (job_id),
    KEY idx_trigger_time (trigger_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '定时任务执行日志';

-- 开放平台应用
CREATE TABLE sys_app
(
    id          BIGINT       NOT NULL COMMENT '主键',
    access_key  VARCHAR(64)  NOT NULL COMMENT 'Access Key',
    secret_key  VARCHAR(255) NOT NULL COMMENT 'Secret Key',
    app_name    VARCHAR(128) NULL COMMENT '应用名称',
    expire_time DATETIME     NULL COMMENT '过期时间',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_access_key (access_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '开放平台应用';

-- 用户-第三方平台绑定
CREATE TABLE sys_user_social
(
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    platform    VARCHAR(32)  NOT NULL COMMENT '平台标识',
    open_id     VARCHAR(128) NOT NULL COMMENT '第三方 openId',
    union_id    VARCHAR(128) NULL COMMENT '第三方 unionId',
    nickname    VARCHAR(64)  NULL COMMENT '第三方昵称',
    avatar      VARCHAR(512) NULL COMMENT '第三方头像',
    access_token VARCHAR(512) NULL COMMENT '第三方 accessToken',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    UNIQUE KEY uk_platform_open (platform, open_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户-第三方平台绑定';

-- 权限模板
CREATE TABLE sys_auth_template
(
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '模板名称',
    code        VARCHAR(64)  NOT NULL COMMENT '模板编码',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '权限模板';

-- 权限模板-菜单关联
CREATE TABLE sys_template_menu
(
    template_id BIGINT NOT NULL COMMENT '模板 ID',
    menu_id     BIGINT NOT NULL COMMENT '菜单 ID',
    PRIMARY KEY (template_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '权限模板-菜单关联';
