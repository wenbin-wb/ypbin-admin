-- =============================================================
-- 修正无效菜单图标 + 恢复定时任务日志独立菜单（汇总页）
-- carbon:organization / carbon:monitoring 非有效图标名（iconify 404），前端不显示图标。
-- 任务日志改为：独立汇总菜单（所有任务）+ 任务列表行内抽屉（单任务），两者共存。
-- =============================================================

-- 修正组织管理、系统监控分类的图标为有效的 carbon 图标名
UPDATE sys_menu SET icon = 'carbon:tree-view-alt' WHERE id = 3001 AND name = 'OrgManage';
UPDATE sys_menu SET icon = 'carbon:activity' WHERE id = 3004 AND name = 'MonitorManage';

-- 恢复定时任务日志独立菜单，指向真实汇总页 /system/job/log/list
-- （幂等：先删可能残留的旧记录，再插入指向正确组件的记录）
DELETE FROM sys_menu WHERE id = 2951;
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2951, 3006, 'SystemJobLog', 'menu', '/system/job/log', '/system/job/log/list', 'system:job:list', 'system.jobLog.title', 'carbon:document', 2, NOW(), 1, 0);
