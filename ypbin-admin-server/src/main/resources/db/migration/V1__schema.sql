-- =============================================================
-- ypbin-admin 建表脚本（含全部表最终结构，唯一结构来源）
-- 约定：BaseEntity 公共列 id / create_user / create_time / update_user / update_time / status / is_deleted
--       TenantBaseEntity 额外含 tenant_id
-- =============================================================

-- 用户表
CREATE TABLE sys_user
(
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NULL COMMENT '租户 ID',
    username        VARCHAR(64)  NOT NULL COMMENT '登录账号',
    user_type       VARCHAR(24)  NOT NULL DEFAULT 'TENANT' COMMENT '用户类型：PLATFORM 平台用户 TENANT 租户用户',
    password        VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt）',
    real_name       VARCHAR(64)  NULL COMMENT '真实姓名',
    nickname        VARCHAR(64)  NULL COMMENT '昵称',
    dept_id         BIGINT       NULL COMMENT '部门 ID',
    avatar          VARCHAR(255) NULL COMMENT '头像',
    phone           VARCHAR(20)  NULL COMMENT '手机号（跨租户登录身份，全局唯一）',
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
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    KEY idx_user_type (user_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统用户';

-- 角色表
CREATE TABLE sys_role
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NULL COMMENT '租户 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    code        VARCHAR(64)  NOT NULL COMMENT '角色标识',
    role_type   VARCHAR(32)  NOT NULL DEFAULT 'TENANT_ROLE' COMMENT '角色类型：PLATFORM_SUPER 平台超级管理员 TENANT_ROLE 租户角色',
    data_scope  TINYINT      NULL DEFAULT 1 COMMENT '数据范围：1 全部 2 本部门及以下 3 本部门 4 仅本人 5 自定义',
    sort        INT          NULL DEFAULT 0 COMMENT '显示排序',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_role_type (role_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统角色';

-- 部门表
CREATE TABLE sys_dept
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NULL COMMENT '租户 ID',
    pid         BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '部门名称',
    sort        INT          NULL DEFAULT 0 COMMENT '显示排序',
    leader      VARCHAR(64)  NULL COMMENT '负责人',
    phone       VARCHAR(20)  NULL COMMENT '联系电话',
    email       VARCHAR(128) NULL COMMENT '邮箱',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统部门';

-- 菜单表（全局，不隔离租户）
CREATE TABLE sys_menu
(
    id                    BIGINT       NOT NULL COMMENT '主键',
    pid                   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单 ID',
    name                  VARCHAR(64)  NOT NULL COMMENT '菜单名称（路由 name）',
    type                  VARCHAR(16)  NOT NULL COMMENT '类型：catalog/menu/button/embedded/link',
    platform_only         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否仅平台用户可见：1 是 0 否',
    path                  VARCHAR(255) NULL COMMENT '路由路径',
    component             VARCHAR(255) NULL COMMENT '组件路径',
    auth_code             VARCHAR(128) NULL COMMENT '权限标识',
    redirect              VARCHAR(255) NULL COMMENT '重定向',
    title                 VARCHAR(64)  NULL COMMENT '标题',
    icon                  VARCHAR(128) NULL COMMENT '图标',
    active_icon           VARCHAR(128) NULL COMMENT '激活图标',
    sort                  INT          NULL DEFAULT 0 COMMENT '显示排序',
    keep_alive            TINYINT      NULL COMMENT '是否缓存：1 是 0 否',
    hide_in_menu          TINYINT      NULL COMMENT '是否隐藏：1 是 0 否',
    iframe_src            VARCHAR(255) NULL COMMENT '内嵌地址',
    link                  VARCHAR(255) NULL COMMENT '外链地址',
    active_path           VARCHAR(255) NULL COMMENT '高亮的菜单路径',
    affix_tab             TINYINT      NULL COMMENT '是否固定标签页：1 是 0 否',
    badge                 VARCHAR(64)  NULL COMMENT '徽标内容',
    badge_type            VARCHAR(16)  NULL COMMENT '徽标类型：dot 点 normal 文字',
    badge_variants        VARCHAR(16)  NULL COMMENT '徽标样式',
    hide_children_in_menu TINYINT      NULL COMMENT '是否隐藏子菜单：1 是 0 否',
    hide_in_breadcrumb    TINYINT      NULL COMMENT '是否在面包屑中隐藏：1 是 0 否',
    hide_in_tab           TINYINT      NULL COMMENT '是否在标签栏中隐藏：1 是 0 否',
    create_user           BIGINT       NULL COMMENT '创建人',
    create_time           DATETIME     NULL COMMENT '创建时间',
    update_user           BIGINT       NULL COMMENT '更新人',
    update_time           DATETIME     NULL COMMENT '更新时间',
    status                TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统菜单';

-- 租户表（全局，不隔离租户）
CREATE TABLE sys_tenant
(
    id            BIGINT       NOT NULL COMMENT '主键',
    name          VARCHAR(64)  NOT NULL COMMENT '租户名称',
    code          VARCHAR(64)  NOT NULL COMMENT '租户编码',
    template_id   BIGINT       NULL COMMENT '所属权限模板 ID',
    contact_name  VARCHAR(64)  NULL COMMENT '联系人',
    contact_phone VARCHAR(20)  NULL COMMENT '联系电话',
    expire_date   DATE         NULL COMMENT '到期时间',
    remark        VARCHAR(255) NULL COMMENT '备注',
    create_user   BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NULL COMMENT '创建时间',
    update_user   BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NULL COMMENT '更新时间',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
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
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '字典名称',
    code        VARCHAR(64)  NOT NULL COMMENT '字典编码（字典类型）',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '数据字典类型';

-- 数据字典项
CREATE TABLE sys_dict_item
(
    id          BIGINT       NOT NULL COMMENT '主键',
    dict_id     BIGINT       NOT NULL COMMENT '所属字典 ID',
    label       VARCHAR(64)  NOT NULL COMMENT '字典项标签',
    value       VARCHAR(64)  NOT NULL COMMENT '字典项值',
    color       VARCHAR(32)  NULL COMMENT '展示颜色',
    sort        INT          NULL DEFAULT 0 COMMENT '显示排序',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
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
    id                 BIGINT       NOT NULL COMMENT '主键',
    client_id          VARCHAR(64)  NOT NULL COMMENT '客户端 ID',
    client_secret      VARCHAR(255) NULL COMMENT '客户端密钥',
    client_type        VARCHAR(16)  NULL COMMENT '客户端类型',
    auth_types         VARCHAR(128) NULL COMMENT '认证方式，逗号分隔',
    timeout            BIGINT       NULL COMMENT 'Token 有效期（秒）',
    active_timeout     BIGINT       NULL COMMENT 'Token 活跃超时（秒）',
    concurrent_enabled TINYINT      NULL COMMENT '是否允许多端登录',
    max_login_count    INT          NULL COMMENT '最大登录数，-1 不限制',
    remark             VARCHAR(255) NULL COMMENT '备注',
    create_user        BIGINT       NULL COMMENT '创建人',
    create_time        DATETIME     NULL COMMENT '创建时间',
    update_user        BIGINT       NULL COMMENT '更新人',
    update_time        DATETIME     NULL COMMENT '更新时间',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '登录客户端';

-- 岗位
CREATE TABLE sys_post
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NULL COMMENT '租户 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '岗位名称',
    code        VARCHAR(64)  NOT NULL COMMENT '岗位编码',
    category    VARCHAR(32)  NULL COMMENT '岗位分类',
    sort        INT          NULL DEFAULT 0 COMMENT '排序',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
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
    id             BIGINT        NOT NULL COMMENT '主键',
    platform       VARCHAR(32)   NULL COMMENT '存储平台',
    bucket         VARCHAR(128)  NULL COMMENT '存储桶',
    url            VARCHAR(512)  NULL COMMENT '文件 URL',
    original_name  VARCHAR(255)  NULL COMMENT '原始文件名',
    file_name      VARCHAR(255)  NULL COMMENT '存储文件名',
    path           VARCHAR(512)  NULL COMMENT '存储路径（相对存储桶，含文件名）',
    size           BIGINT        NULL COMMENT '文件大小（字节）',
    content_type   VARCHAR(128)  NULL COMMENT 'MIME 类型',
    extension      VARCHAR(32)   NULL COMMENT '文件扩展名',
    hash           VARCHAR(128)  NULL COMMENT '文件哈希',
    upload_user_id BIGINT        NULL COMMENT '上传人',
    module         VARCHAR(32)   NULL COMMENT '所属业务模块',
    storage_status VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE' COMMENT '存储状态：ACTIVE DELETE_FAILED STORAGE_DELETED LOCATOR_MISSING',
    error_message  VARCHAR(1024) NULL COMMENT '最近一次存储操作错误',
    create_user    BIGINT        NULL COMMENT '创建人',
    create_time    DATETIME      NULL COMMENT '创建时间',
    update_user    BIGINT        NULL COMMENT '更新人',
    update_time    DATETIME      NULL COMMENT '更新时间',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文件管理';

-- 系统公告（租户表）
CREATE TABLE sys_notice
(
    id               BIGINT        NOT NULL COMMENT '主键',
    tenant_id        BIGINT        NOT NULL COMMENT '租户 ID',
    title            VARCHAR(255)  NOT NULL COMMENT '标题',
    content          MEDIUMTEXT    NULL COMMENT '公告内容（富文本）',
    cover            VARCHAR(512)  NULL COMMENT '封面图 URL',
    notice_type      TINYINT       NULL COMMENT '类型：1 通知 2 公告',
    notice_scope     TINYINT       NULL DEFAULT 1 COMMENT '通知范围：1 全体 2 指定角色 3 指定部门 4 指定用户',
    scope_target_ids VARCHAR(1024) NULL COMMENT '范围目标 ID 集合（逗号分隔）',
    notify_methods   VARCHAR(64)   NULL COMMENT '通知方式（逗号分隔：site/email/sms）',
    is_top           TINYINT       NULL DEFAULT 0 COMMENT '是否置顶：1 是 0 否',
    publish_type     TINYINT       NULL DEFAULT 1 COMMENT '发布方式：1 立即 2 定时',
    publish_status   TINYINT       NULL DEFAULT 2 COMMENT '发布状态：0 草稿 1 待发布 2 已发布 3 已撤回',
    publish_version  BIGINT        NOT NULL DEFAULT 0 COMMENT '发布版本',
    scheduled_time   DATETIME      NULL COMMENT '定时发布时间',
    publish_time     DATETIME      NULL COMMENT '实际发布时间',
    effective_time   DATETIME      NULL COMMENT '生效时间',
    expire_time      DATETIME      NULL COMMENT '失效时间',
    create_user      BIGINT        NULL COMMENT '创建人',
    create_time      DATETIME      NULL COMMENT '创建时间',
    update_user      BIGINT        NULL COMMENT '更新人',
    update_time      DATETIME      NULL COMMENT '更新时间',
    status           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_notice_tenant_publish (tenant_id, publish_status, scheduled_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统公告';

-- 用户消息（站内信）
CREATE TABLE sys_message
(
    id               BIGINT       NOT NULL COMMENT '主键',
    tenant_id        BIGINT       NULL COMMENT '租户 ID',
    notice_id        BIGINT       NULL COMMENT '公告 ID',
    publish_version  BIGINT       NULL COMMENT '公告发布版本',
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
    KEY idx_receiver (receiver_user_id),
    UNIQUE KEY uk_message_notice_receiver (tenant_id, notice_id, publish_version, receiver_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户消息';

-- 公告投递记录
CREATE TABLE sys_notice_delivery
(
    id               BIGINT        NOT NULL COMMENT '主键',
    tenant_id        BIGINT        NOT NULL COMMENT '租户 ID',
    notice_id        BIGINT        NOT NULL COMMENT '公告 ID',
    publish_version  BIGINT        NOT NULL COMMENT '公告发布版本',
    receiver_user_id BIGINT        NOT NULL COMMENT '接收人用户 ID',
    channel          VARCHAR(16)   NOT NULL COMMENT '投递通道：site/email/sms',
    target_address   VARCHAR(255)  NULL COMMENT '目标地址',
    delivery_status  VARCHAR(16)   NOT NULL COMMENT '投递状态',
    retry_count      INT           NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time  DATETIME      NULL COMMENT '下次重试时间',
    error_message    VARCHAR(1000) NULL COMMENT '错误信息',
    delivered_time   DATETIME      NULL COMMENT '投递成功时间',
    create_time      DATETIME      NOT NULL COMMENT '创建时间',
    update_time      DATETIME      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_delivery (tenant_id, notice_id, publish_version, receiver_user_id, channel),
    KEY idx_delivery_retry (delivery_status, next_retry_time, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '公告投递记录';

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
    id           BIGINT       NOT NULL COMMENT '主键',
    user_id      BIGINT       NOT NULL COMMENT '用户 ID',
    platform     VARCHAR(32)  NOT NULL COMMENT '平台标识',
    open_id      VARCHAR(128) NOT NULL COMMENT '第三方 openId',
    union_id     VARCHAR(128) NULL COMMENT '第三方 unionId',
    nickname     VARCHAR(64)  NULL COMMENT '第三方昵称',
    avatar       VARCHAR(512) NULL COMMENT '第三方头像',
    access_token VARCHAR(512) NULL COMMENT '第三方 accessToken',
    create_user  BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NULL COMMENT '创建时间',
    update_user  BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NULL COMMENT '更新时间',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
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

-- 商业授权（全局表，已在 application.yml 的 tenant.ignore-tables 登记）
-- 授权本身可绑定某业务租户（tenant_id 字段），与表级租户隔离无关。
-- JSON 列（fingerprints/modules/quotas/attributes）由 MyBatis-Plus JacksonTypeHandler 读写。
CREATE TABLE sys_license
(
    id             BIGINT       NOT NULL COMMENT '主键',
    license_id     VARCHAR(64)  NULL COMMENT '授权编号（签发时生成，全局唯一，用于联机校验与吊销）',
    subject        VARCHAR(128) NOT NULL COMMENT '被授权方名称',
    remark         VARCHAR(255) NULL COMMENT '供应方备注',
    fingerprints   JSON         NULL COMMENT '允许运行的机器指纹列表（为空表示不限机器）',
    tenant_id      VARCHAR(64)  NULL COMMENT '绑定租户标识（为空表示不限租户）',
    effective_at   DATETIME     NULL COMMENT '生效时间（早于此时间视为未生效）',
    expire_at      DATETIME     NULL COMMENT '到期时间（为空表示永久授权）',
    grace_days     INT          NULL DEFAULT 0 COMMENT '过期后的宽限天数（此期间状态为非法可用）',
    modules        JSON         NULL COMMENT '授权的功能模块标识集合（为空表示不做模块级限制）',
    quotas         JSON         NULL COMMENT '业务额度限制（如 device=100、user=500）',
    attributes     JSON         NULL COMMENT '自定义扩展参数',
    delivery_mode  VARCHAR(16)  NOT NULL COMMENT '交付模式：CODE 内联授权码 / FILE 授权文件',
    source         VARCHAR(16)  NOT NULL DEFAULT 'manual' COMMENT '签发来源：manual 手工 / payment 支付（预留）',
    app_id         BIGINT       NULL COMMENT '联机开放应用ID（签发时按被授权方自动创建或复用）',
    auth_code      MEDIUMTEXT   NULL COMMENT '签发产物：Base64 授权串（审批通过后写入）',
    approve_status VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT/PENDING/ISSUED/REJECTED/REVOKED',
    approve_user   BIGINT       NULL COMMENT '审批人（签发/驳回操作人，须不同于创建人）',
    approve_time   DATETIME     NULL COMMENT '审批时间',
    reject_reason  VARCHAR(255) NULL COMMENT '驳回原因',
    create_user    BIGINT       NULL COMMENT '创建人',
    create_time    DATETIME     NULL COMMENT '创建时间',
    update_user    BIGINT       NULL COMMENT '更新人',
    update_time    DATETIME     NULL COMMENT '更新时间',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_license_id (license_id),
    KEY idx_approve_status (approve_status),
    KEY idx_app_id (app_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商业授权';

-- 一次性初始化状态（平台管理员 bootstrap 的 CAS 抢占依据）
CREATE TABLE sys_bootstrap_state
(
    bootstrap_key VARCHAR(64) NOT NULL COMMENT '初始化任务标识',
    state         VARCHAR(16) NOT NULL COMMENT '状态：PENDING RUNNING COMPLETED',
    owner         VARCHAR(64) NULL COMMENT '执行实例标识',
    completed_at  DATETIME    NULL COMMENT '完成时间',
    create_time   DATETIME    NOT NULL COMMENT '创建时间',
    update_time   DATETIME    NOT NULL COMMENT '更新时间',
    PRIMARY KEY (bootstrap_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '一次性初始化状态';
