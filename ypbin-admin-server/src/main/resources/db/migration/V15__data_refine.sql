-- =============================================================
-- 数据整理：回填种子创建人 + 菜单分类为顶级目录
-- 种子数据 create_user 统一补为超级管理员(1)，使创建人翻译字段可见；
-- 系统管理平铺菜单归类为六个顶级分类目录（组织/权限/系统/监控/租户/任务），
-- 并删除空的 System 容器、补建缺失的租户菜单与任务日志菜单。
-- =============================================================

-- 回填种子创建人
UPDATE sys_tenant SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_dept SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_user SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_role SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_dict SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_dict_item SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_config SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_client SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_job SET create_user = 1 WHERE create_user IS NULL;

-- 新增六个顶级分类目录
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (3001, 0, 'OrgManage', 'catalog', '/system/org', 'BasicLayout', 'system.org.title', 'carbon:organization', 1, NOW(), 1, 0),
       (3002, 0, 'AuthManage', 'catalog', '/system/auth', 'BasicLayout', 'system.auth.title', 'carbon:security', 2, NOW(), 1, 0),
       (3003, 0, 'SysManage', 'catalog', '/system/sys', 'BasicLayout', 'system.sys.title', 'carbon:settings-adjust', 3, NOW(), 1, 0),
       (3004, 0, 'MonitorManage', 'catalog', '/system/monitor', 'BasicLayout', 'system.monitor.title', 'carbon:monitoring', 4, NOW(), 1, 0),
       (3005, 0, 'TenantManage', 'catalog', '/system/tenant', 'BasicLayout', 'system.tenant.title', 'carbon:building', 5, NOW(), 1, 0),
       (3006, 0, 'JobManage', 'catalog', '/system/jobm', 'BasicLayout', 'system.job.title', 'carbon:timer', 6, NOW(), 1, 0);

-- 归类：组织管理
UPDATE sys_menu SET pid = 3001 WHERE id IN (240, 2500, 210);
-- 归类：权限管理
UPDATE sys_menu SET pid = 3002 WHERE id IN (230, 220, 290, 2900);
-- 归类：系统管理
UPDATE sys_menu SET pid = 3003 WHERE id IN (250, 260, 2600, 2700);
-- 归类：监控管理
UPDATE sys_menu SET pid = 3004 WHERE id IN (270, 280);
-- 归类：任务管理
UPDATE sys_menu SET pid = 3006 WHERE id IN (2800);

-- 删除空的 System 容器目录（原平铺菜单已全部移走）
DELETE FROM sys_menu WHERE id = 2;

-- 补建租户管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2950, 3005, 'SystemTenant', 'menu', '/system/tenant', '/system/tenant/list', 'system:tenant:list', 'system.tenant.title', 'carbon:building-insights-2', 1, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (295001, 2950, 'SystemTenantAdd', 'button', 'system:tenant:add', 'common.create', 1, NOW(), 1, 0),
       (295002, 2950, 'SystemTenantEdit', 'button', 'system:tenant:edit', 'common.edit', 2, NOW(), 1, 0),
       (295003, 2950, 'SystemTenantDelete', 'button', 'system:tenant:delete', 'common.delete', 3, NOW(), 1, 0);

-- 补建定时任务日志菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2951, 3006, 'SystemJobLog', 'menu', '/system/job/log', '/system/job/log', 'system:job:list', 'system.jobLog.title', 'carbon:document', 2, NOW(), 1, 0);
