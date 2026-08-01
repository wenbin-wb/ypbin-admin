-- =============================================================
-- 登录客户端管理 + 在线用户菜单，全局表
-- =============================================================

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

-- 默认 Web 管理后台客户端
INSERT INTO sys_client (id, client_id, client_type, auth_types, timeout, active_timeout, concurrent_enabled, create_time, status, is_deleted)
VALUES (1, 'web-admin', 'WEB', 'ACCOUNT,PHONE,EMAIL', 86400, 1800, 1, NOW(), 1, 0);

-- 在线用户管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (280, 2, 'SystemOnlineUser', 'menu', '/system/online-user', '/system/online-user/list', 'system:online-user:list', 'system.onlineUser.title', 'carbon:user-online', 8, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (28001, 280, 'SystemOnlineUserKickout', 'button', 'system:online-user:kickout', 'common.kickout', 1, NOW(), 1, 0);

-- 客户端管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (290, 2, 'SystemClient', 'menu', '/system/client', '/system/client/list', 'system:client:list', 'system.client.title', 'carbon:application', 9, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (29001, 290, 'SystemClientAdd', 'button', 'system:client:add', 'common.create', 1, NOW(), 1, 0),
       (29002, 290, 'SystemClientEdit', 'button', 'system:client:edit', 'common.edit', 2, NOW(), 1, 0),
       (29003, 290, 'SystemClientDelete', 'button', 'system:client:delete', 'common.delete', 3, NOW(), 1, 0);
