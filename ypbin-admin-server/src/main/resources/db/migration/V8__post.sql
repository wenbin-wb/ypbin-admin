-- =============================================================
-- 岗位管理 + 用户-岗位关联（租户隔离）
-- =============================================================

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

CREATE TABLE sys_user_post
(
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    post_id BIGINT NOT NULL COMMENT '岗位 ID',
    PRIMARY KEY (user_id, post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户-岗位关联';

-- 岗位管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2500, 2, 'SystemPost', 'menu', '/system/post', '/system/post/list', 'system:post:list', 'system.post.title', 'carbon:id-management', 10, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (250001, 2500, 'SystemPostAdd', 'button', 'system:post:add', 'common.create', 1, NOW(), 1, 0),
       (250002, 2500, 'SystemPostEdit', 'button', 'system:post:edit', 'common.edit', 2, NOW(), 1, 0),
       (250003, 2500, 'SystemPostDelete', 'button', 'system:post:delete', 'common.delete', 3, NOW(), 1, 0);
