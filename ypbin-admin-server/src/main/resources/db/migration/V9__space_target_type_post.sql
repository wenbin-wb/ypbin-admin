-- 空间关联对象类型补充「岗位」（post）。
--
-- 背景：前端岗位管理（/system/post）复用空间关联面板，调用 targetType=post；
-- 而 V8 的空间种子只写入了 dept/user/device/asset/other，
-- SysSpaceRelationServiceImpl#normalizeTargetType 按字典 space_target_type 校验时
-- 会把 post 判为非法（409「关联对象类型不合法」）。
--
-- 按「字典是取值单一来源」的设计，新增业务对象类型只需补字典项，空间侧零代码。
-- 刻意不改 V8：已应用的迁移不能改校验和；新库也会按顺序执行本迁移。
-- 幂等：重复执行时 WHERE NOT EXISTS 会跳过。

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'),
       '岗位', 'post', 'processing', 5, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id
                  WHERE d.code = 'space_target_type' AND i.value = 'post');
