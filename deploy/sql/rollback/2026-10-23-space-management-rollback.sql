-- =============================================================
-- 回滚：空间管理（sys_space / sys_space_relation）+ 空间类型字典 + 菜单权限
-- 回滚对象：deploy/sql/migration/2026-10-23-space-management.sql
--           （等价于 001-schema.sql / 002-data.sql 中的「空间管理」段）
--
-- ⚠️ **数据会丢失**：本脚本 DROP 两张表，空间树与全部关联（部门/人员/设备挂在哪些空间）一并删除。
--    执行前必须先备份：
--      mysqldump -uroot -p ypbin_admin sys_space sys_space_relation > space-backup.sql
--
-- 执行方式：
--   mysql -uroot -p ypbin_admin < 2026-10-23-space-management-rollback.sql
--
-- 顺序（必须按此序，否则外键语义/残留数据会不一致）：
--   1) 删授权（role_menu / template_menu）—— 先删引用，再删被引用的菜单
--   2) 删菜单（按钮 → 页面）
--   3) 删字典项 → 字典类型
--   4) 删关联表 → 空间表（关联引用空间）
--
-- 幂等性：全部用 DELETE / DROP TABLE IF EXISTS，可重复执行。
-- =============================================================

-- ---------- 1) 删授权 ----------
-- 按 auth_code 反查真实 id（迁移侧已改为动态分配 id，写死 241x 会漏删/误删）
DELETE rm FROM sys_role_menu rm INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                      'system:space:delete', 'system:space:bind');
DELETE tm FROM sys_template_menu tm INNER JOIN sys_menu m ON m.id = tm.menu_id
WHERE m.auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                      'system:space:delete', 'system:space:bind');

-- ---------- 2) 删菜单（先按钮后页面） ----------
-- 先按钮后页面：按钮的 pid 指向页面，顺序反了会留下悬空 pid（虽无外键，但语义脏）
DELETE FROM sys_menu WHERE auth_code IN ('system:space:add', 'system:space:edit',
                                         'system:space:delete', 'system:space:bind');
DELETE FROM sys_menu WHERE auth_code = 'system:space:list';

-- ---------- 3) 删字典（先项后类型） ----------
-- 按 code 反查真实 id（迁移侧已改为动态 id）
DELETE i FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id
WHERE d.code IN ('space_type', 'space_target_type');
DELETE FROM sys_dict WHERE code IN ('space_type', 'space_target_type');

-- ---------- 4) 删表（先关联后空间） ----------
DROP TABLE IF EXISTS sys_space_relation;
DROP TABLE IF EXISTS sys_space;
