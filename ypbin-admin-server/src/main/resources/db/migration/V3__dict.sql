-- =============================================================
-- 数据字典：字典类型 + 字典项（全局共享，不隔离租户）
-- =============================================================

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

-- 内置字典：状态、性别
INSERT INTO sys_dict (id, name, code, remark, create_time, status, is_deleted)
VALUES (1, '系统状态', 'sys_status', '通用启用/禁用状态', NOW(), 1, 0),
       (2, '性别', 'sys_gender', '用户性别', NOW(), 1, 0);

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_time, status, is_deleted)
VALUES (11, 1, '正常', '1', 'success', 1, NOW(), 1, 0),
       (12, 1, '禁用', '0', 'error', 2, NOW(), 1, 0),
       (21, 2, '未知', '0', 'default', 1, NOW(), 1, 0),
       (22, 2, '男', '1', 'processing', 2, NOW(), 1, 0),
       (23, 2, '女', '2', 'warning', 3, NOW(), 1, 0);

-- 字典管理菜单 + 按钮权限
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (250, 2, 'SystemDict', 'menu', '/system/dict', '/system/dict/list', 'system:dict:list', 'system.dict.title', 'carbon:catalog', 5, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (25001, 250, 'SystemDictAdd', 'button', 'system:dict:add', 'common.create', 1, NOW(), 1, 0),
       (25002, 250, 'SystemDictEdit', 'button', 'system:dict:edit', 'common.edit', 2, NOW(), 1, 0),
       (25003, 250, 'SystemDictDelete', 'button', 'system:dict:delete', 'common.delete', 3, NOW(), 1, 0);
