-- =============================================================
-- 数据权限：角色-部门关联（自定义数据范围用），全局表
-- =============================================================

CREATE TABLE sys_role_dept
(
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    dept_id BIGINT NOT NULL COMMENT '部门 ID',
    PRIMARY KEY (role_id, dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色-部门关联';
