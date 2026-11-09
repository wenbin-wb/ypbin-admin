-- 空间关联对象类型补充「岗位」（post）。
--
-- 背景：前端岗位管理（/system/post）复用空间关联面板，调用 targetType=post；
-- 而字典 space_target_type 只有 dept/user/device/asset/other，
-- SysSpaceRelationServiceImpl#normalizeTargetType 按字典校验时会返回 409「关联对象类型不合法」。
--
-- 字典是取值的单一来源：新增业务对象类型只需补字典项，空间侧零代码。
-- 幂等：已有该字典项时跳过。

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'),
       '岗位', 'post', 'processing', 5, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id
                  WHERE d.code = 'space_target_type' AND i.value = 'post');
