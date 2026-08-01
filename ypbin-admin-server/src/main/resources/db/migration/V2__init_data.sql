-- =============================================================
-- ypbin-admin 初始化种子数据
-- 默认租户 1；超级管理员 admin/admin123（BCrypt）；super 角色跳过权限校验
-- 固定 ID 便于关联，雪花 ID 从业务新增时生成
-- =============================================================

-- 默认租户
INSERT INTO sys_tenant (id, name, code, contact_name, remark, create_time, status, is_deleted)
VALUES (1, '默认租户', 'default', 'admin', '系统内置默认租户', NOW(), 1, 0);

-- 默认部门
INSERT INTO sys_dept (id, tenant_id, pid, name, sort, leader, create_time, status, is_deleted)
VALUES (1, 1, 0, '总公司', 1, 'admin', NOW(), 1, 0);

-- 超级管理员
INSERT INTO sys_user (id, tenant_id, username, password, real_name, nickname, dept_id, gender,
                      remark, pwd_reset_time, create_time, status, is_deleted)
VALUES (1, 1, 'admin', '$2a$10$ZuXfY6FkrI0fEGRoX9AlZuo3r/askEJEVHz6rKwKMrDVCpttLIq82',
        '超级管理员', '超级管理员', 1, 1, '系统内置超级管理员', NOW(), NOW(), 1, 0);

-- 超级管理员角色
INSERT INTO sys_role (id, tenant_id, name, code, data_scope, sort, remark, create_time, status, is_deleted)
VALUES (1, 1, '超级管理员', 'super', 1, 1, '系统内置超级管理员角色，拥有全部权限', NOW(), 1, 0);

-- 用户-角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- =============================================================
-- 菜单树
-- =============================================================
-- 仪表盘
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (1, 0, 'Dashboard', 'catalog', '/dashboard', 'BasicLayout', 'page.dashboard.title', 'lucide:layout-dashboard', -1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, keep_alive, sort, create_time, status, is_deleted)
VALUES (101, 1, 'Analytics', 'menu', '/dashboard/analytics', '/dashboard/analytics/index', 'page.dashboard.analytics', 'lucide:area-chart', 1, 1, NOW(), 1, 0),
       (102, 1, 'Workspace', 'menu', '/dashboard/workspace', '/dashboard/workspace/index', 'page.dashboard.workspace', 'carbon:workspace', 0, 2, NOW(), 1, 0);

-- 系统管理
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (2, 0, 'System', 'catalog', '/system', 'BasicLayout', 'system.title', 'carbon:settings', 9997, NOW(), 1, 0);

-- 用户管理
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (210, 2, 'SystemUser', 'menu', '/system/user', '/system/user/list', 'system:user:list', 'system.user.title', 'carbon:user', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (21001, 210, 'SystemUserAdd', 'button', 'system:user:add', 'common.create', 1, NOW(), 1, 0),
       (21002, 210, 'SystemUserEdit', 'button', 'system:user:edit', 'common.edit', 2, NOW(), 1, 0),
       (21003, 210, 'SystemUserDelete', 'button', 'system:user:delete', 'common.delete', 3, NOW(), 1, 0);

-- 角色管理
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (220, 2, 'SystemRole', 'menu', '/system/role', '/system/role/list', 'system:role:list', 'system.role.title', 'carbon:user-role', 2, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (22001, 220, 'SystemRoleAdd', 'button', 'system:role:add', 'common.create', 1, NOW(), 1, 0),
       (22002, 220, 'SystemRoleEdit', 'button', 'system:role:edit', 'common.edit', 2, NOW(), 1, 0),
       (22003, 220, 'SystemRoleDelete', 'button', 'system:role:delete', 'common.delete', 3, NOW(), 1, 0);

-- 菜单管理
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (230, 2, 'SystemMenu', 'menu', '/system/menu', '/system/menu/list', 'system:menu:list', 'system.menu.title', 'carbon:menu', 3, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (23001, 230, 'SystemMenuAdd', 'button', 'system:menu:add', 'common.create', 1, NOW(), 1, 0),
       (23002, 230, 'SystemMenuEdit', 'button', 'system:menu:edit', 'common.edit', 2, NOW(), 1, 0),
       (23003, 230, 'SystemMenuDelete', 'button', 'system:menu:delete', 'common.delete', 3, NOW(), 1, 0);

-- 部门管理
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (240, 2, 'SystemDept', 'menu', '/system/dept', '/system/dept/list', 'system:dept:list', 'system.dept.title', 'carbon:container-services', 4, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (24001, 240, 'SystemDeptAdd', 'button', 'system:dept:add', 'common.create', 1, NOW(), 1, 0),
       (24002, 240, 'SystemDeptEdit', 'button', 'system:dept:edit', 'common.edit', 2, NOW(), 1, 0),
       (24003, 240, 'SystemDeptDelete', 'button', 'system:dept:delete', 'common.delete', 3, NOW(), 1, 0);
