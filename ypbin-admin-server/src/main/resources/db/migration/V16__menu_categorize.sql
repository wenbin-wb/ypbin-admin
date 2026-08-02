-- =============================================================
-- 菜单分类整理：在系统管理(pid=2)下新增六个二级目录，将平铺菜单归类
-- 目录 id 避开已有段(2xxx)，使用 3001~3006；同时补建缺失的租户菜单与任务日志菜单
-- =============================================================

-- 新增二级目录
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (3001, 2, 'OrgManage', 'catalog', '/system/org', 'BasicLayout', 'system.org.title', 'carbon:organization', 1, NOW(), 1, 0),
       (3002, 2, 'AuthManage', 'catalog', '/system/auth', 'BasicLayout', 'system.auth.title', 'carbon:security', 2, NOW(), 1, 0),
       (3003, 2, 'SysManage', 'catalog', '/system/sys', 'BasicLayout', 'system.sys.title', 'carbon:settings-adjust', 3, NOW(), 1, 0),
       (3004, 2, 'MonitorManage', 'catalog', '/system/monitor', 'BasicLayout', 'system.monitor.title', 'carbon:monitoring', 4, NOW(), 1, 0),
       (3005, 2, 'TenantManage', 'catalog', '/system/tenant', 'BasicLayout', 'system.tenant.title', 'carbon:building', 5, NOW(), 1, 0),
       (3006, 2, 'JobManage', 'catalog', '/system/jobm', 'BasicLayout', 'system.job.title', 'carbon:timer', 6, NOW(), 1, 0);

-- 组织管理：部门、岗位、用户
UPDATE sys_menu SET pid = 3001 WHERE id IN (240, 2500, 210);
-- 权限管理：菜单、角色、客户端、开放应用
UPDATE sys_menu SET pid = 3002 WHERE id IN (230, 220, 290, 2900);
-- 系统管理：字典、参数、文件、公告
UPDATE sys_menu SET pid = 3003 WHERE id IN (250, 260, 2600, 2700);
-- 监控管理：日志、在线用户
UPDATE sys_menu SET pid = 3004 WHERE id IN (270, 280);
-- 任务管理：定时任务
UPDATE sys_menu SET pid = 3006 WHERE id IN (2800);

-- 补建租户管理菜单（此前只有控制器、无菜单/权限）
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2950, 3005, 'SystemTenant', 'menu', '/system/tenant', '/system/tenant/list', 'system:tenant:list', 'system.tenant.title', 'carbon:building-insights-2', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (295001, 2950, 'SystemTenantAdd', 'button', 'system:tenant:add', 'common.create', 1, NOW(), 1, 0),
       (295002, 2950, 'SystemTenantEdit', 'button', 'system:tenant:edit', 'common.edit', 2, NOW(), 1, 0),
       (295003, 2950, 'SystemTenantDelete', 'button', 'system:tenant:delete', 'common.delete', 3, NOW(), 1, 0);

-- 补建定时任务日志菜单（查询任务执行日志）
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2951, 3006, 'SystemJobLog', 'menu', '/system/job/log', '/system/job/log', 'system:job:list', 'system.jobLog.title', 'carbon:document', 2, NOW(), 1, 0);
