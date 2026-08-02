-- =============================================================
-- 权限模板：租户可用的菜单权限集合，分配给多个租户，登录时按模板过滤可见菜单
-- =============================================================

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

-- 租户表增加模板字段（默认租户使用完整权限模板）
ALTER TABLE sys_tenant ADD COLUMN template_id BIGINT NULL COMMENT '所属权限模板 ID' AFTER code;

-- 默认模板：全部权限（授权所有菜单）
INSERT INTO sys_auth_template (id, name, code, remark, create_user, create_time, status, is_deleted)
VALUES (1, '全部权限', 'ALL', '内置模板：拥有全部菜单权限', 1, NOW(), 1, 0);
INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, id FROM sys_menu WHERE is_deleted = 0;

-- 默认租户绑定全部权限模板
UPDATE sys_tenant SET template_id = 1 WHERE id = 1;

-- 权限模板管理菜单（归入权限管理分类 3002）
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2952, 3002, 'SystemAuthTemplate', 'menu', '/system/auth-template', '/system/auth-template/list', 'system:auth-template:list', 'system.authTemplate.title', 'carbon:template', 3, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (295201, 2952, 'SystemAuthTemplateAdd', 'button', 'system:auth-template:add', 'common.create', 1, NOW(), 1, 0),
       (295202, 2952, 'SystemAuthTemplateEdit', 'button', 'system:auth-template:edit', 'common.edit', 2, NOW(), 1, 0),
       (295203, 2952, 'SystemAuthTemplateDelete', 'button', 'system:auth-template:delete', 'common.delete', 3, NOW(), 1, 0);
