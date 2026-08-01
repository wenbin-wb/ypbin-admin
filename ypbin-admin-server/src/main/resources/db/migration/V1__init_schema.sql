-- =============================================================
-- ypbin-admin 初始化建表脚本
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
    id          BIGINT       NOT NULL COMMENT '主键',
    pid         BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '菜单名称（路由 name）',
    type        VARCHAR(16)  NOT NULL COMMENT '类型：catalog/menu/button/embedded/link',
    path        VARCHAR(255) NULL COMMENT '路由路径',
    component   VARCHAR(255) NULL COMMENT '组件路径',
    auth_code   VARCHAR(128) NULL COMMENT '权限标识',
    redirect    VARCHAR(255) NULL COMMENT '重定向',
    title       VARCHAR(64)  NULL COMMENT '标题',
    icon        VARCHAR(128) NULL COMMENT '图标',
    active_icon VARCHAR(128) NULL COMMENT '激活图标',
    sort        INT          NULL DEFAULT 0 COMMENT '显示排序',
    keep_alive  TINYINT      NULL COMMENT '是否缓存：1 是 0 否',
    hide_in_menu TINYINT     NULL COMMENT '是否隐藏：1 是 0 否',
    iframe_src  VARCHAR(255) NULL COMMENT '内嵌地址',
    link        VARCHAR(255) NULL COMMENT '外链地址',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统菜单';

-- 租户表（全局，不隔离租户）
CREATE TABLE sys_tenant
(
    id            BIGINT      NOT NULL COMMENT '主键',
    name          VARCHAR(64) NOT NULL COMMENT '租户名称',
    code          VARCHAR(64) NOT NULL COMMENT '租户编码',
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
