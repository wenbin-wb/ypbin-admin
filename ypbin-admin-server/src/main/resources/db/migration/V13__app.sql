-- =============================================================
-- 开放平台应用管理，全局表
-- =============================================================

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

-- 开放应用管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2900, 2, 'SystemApp', 'menu', '/system/app', '/system/app/list', 'system:app:list', 'system.app.title', 'carbon:api', 14, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (290001, 2900, 'SystemAppAdd', 'button', 'system:app:add', 'common.create', 1, NOW(), 1, 0),
       (290002, 2900, 'SystemAppEdit', 'button', 'system:app:edit', 'common.edit', 2, NOW(), 1, 0),
       (290003, 2900, 'SystemAppDelete', 'button', 'system:app:delete', 'common.delete', 3, NOW(), 1, 0);
