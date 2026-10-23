-- =============================================================
-- 增量升级：空间管理（sys_space / sys_space_relation）+ 空间类型字典 + 菜单权限（2026-10-23）
--
-- 适用对象：**已经在跑的库**（表已存在）。全新安装不需要执行本文件——
--           deploy/sql/001-schema.sql 与 002-data.sql 已含最终结构。
-- 为什么单独放子目录：install.sh 用 `deploy/sql/*.sql` 通配执行初始化脚本，
--           本文件放在 migration/ 子目录内**不会**被自动执行（避免在全新库上重复建表报错）。
--
-- 执行方式：
--   mysql -uroot -p ypbin_admin < 2026-10-23-space-management.sql
--
-- 幂等性：本文件用 IF NOT EXISTS / INSERT IGNORE / NOT EXISTS 守卫，**可重复执行**。
--         这与仓内其它一次性迁移不同，是刻意为之：空间是基础主数据，升级脚本被重跑
--         （人工/脚本中断后补跑）的概率高于业务表迁移，重复执行必须安全。
--
-- 回滚：deploy/sql/rollback/2026-10-23-space-management-rollback.sql
--
-- 与 001/002 的等价性：本文件的语句与 001-schema.sql、002-data.sql 中「空间管理」段
--         **语义等价**（建表/字典/菜单/授权一致）。改一边必须同步另一边。
-- =============================================================

-- ---------- 1) 空间主表 ----------
CREATE TABLE IF NOT EXISTS sys_space
(
    id          BIGINT         NOT NULL COMMENT '主键',
    tenant_id   BIGINT         NULL COMMENT '租户 ID',
    pid         BIGINT         NOT NULL DEFAULT 0 COMMENT '父空间 ID，顶级为 0',
    name        VARCHAR(128)   NOT NULL COMMENT '空间名称',
    code        VARCHAR(64)    NULL COMMENT '空间编码（租户内唯一，供业务侧按编码引用；为空则不参与唯一约束）',
    space_type  VARCHAR(32)    NOT NULL COMMENT '空间类型（sys_dict: space_type）',
    level       INT            NOT NULL DEFAULT 1 COMMENT '层级深度（根为 1，由服务端维护）',
    sort        INT            NULL DEFAULT 0 COMMENT '同级排序',
    address     VARCHAR(255)   NULL COMMENT '详细地址（园区/楼栋常用）',
    longitude   DECIMAL(11, 7) NULL COMMENT '经度（WGS84）',
    latitude    DECIMAL(10, 7) NULL COMMENT '纬度（WGS84）',
    floor_no    VARCHAR(16)    NULL COMMENT '所在楼层号（如 1、-1、1F）',
    room_no     VARCHAR(32)    NULL COMMENT '房间号（如 101）',
    area        DECIMAL(12, 2) NULL COMMENT '面积（平方米）',
    capacity    INT            NULL COMMENT '容纳人数/容量',
    manager     VARCHAR(64)    NULL COMMENT '空间负责人',
    phone       VARCHAR(20)    NULL COMMENT '联系电话',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    remark      VARCHAR(500)   NULL COMMENT '备注',
    ext_json    TEXT           NULL COMMENT '扩展属性 JSON（业务自定义字段）',
    create_user BIGINT         NULL COMMENT '创建人',
    create_time DATETIME       NULL COMMENT '创建时间',
    update_user BIGINT         NULL COMMENT '更新人',
    update_time DATETIME       NULL COMMENT '更新时间',
    is_deleted  TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    -- 编码唯一性生成列：未删除取 code，已删除取 NULL（唯一键对 NULL 不去重）
    -- ⇒ 删掉的空间不再占用编码，可被复用。与 001-schema.sql 必须逐字一致。
    code_alive VARCHAR(64) AS (IF(is_deleted = 0, code, NULL)) STORED COMMENT '编码唯一性生成列（仅供唯一键，不读）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_space_tenant_code (tenant_id, code_alive),
    KEY idx_space_tenant_pid (tenant_id, pid),
    KEY idx_space_tenant_type (tenant_id, space_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '空间（楼栋/楼层/房间/办公室/仓库等）';

-- ---------- 2) 空间与业务对象的多态关联表 ----------
CREATE TABLE IF NOT EXISTS sys_space_relation
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NULL COMMENT '租户 ID',
    space_id    BIGINT       NOT NULL COMMENT '空间 ID（sys_space.id）',
    target_type VARCHAR(32)  NOT NULL COMMENT '关联对象类型（sys_dict: space_target_type）',
    target_id   BIGINT       NOT NULL COMMENT '关联对象 ID',
    is_primary  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否主空间：1 是（一个对象最多一个主空间）0 否',
    -- 主空间唯一性生成列：只有「is_primary=1」取 1，其余取 NULL ⇒ 唯一键表达「一对象一主空间」。
    -- 与 001-schema.sql 必须逐字一致。
    primary_marker TINYINT   AS (IF(is_primary = 1, 1, NULL)) STORED COMMENT '主空间唯一性生成列（仅供唯一键，不读）',
    remark      VARCHAR(255) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_space_relation (tenant_id, space_id, target_type, target_id),
    UNIQUE KEY uk_space_relation_primary (tenant_id, target_type, target_id, primary_marker),
    KEY idx_space_relation_target (tenant_id, target_type, target_id),
    KEY idx_space_relation_space (tenant_id, space_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '空间与业务对象的关联（多态）';

-- ---------- 2b) 兼容旧版迁移：给已存在的表补生成列与唯一键 ----------
-- 背景：本迁移的早期版本建表时没有 code_alive / primary_marker 两个生成列，
-- 导致「升级库」缺少两个数据库级兜底：① 逻辑删除后编码不可复用；② 并发下可能出现两个主空间。
-- 这里用 information_schema 判存在后 ALTER，**幂等**（重复执行不会报 Duplicate column/key）。
-- 结构与 001-schema.sql 的最终状态逐字一致。
SET @has_code_alive := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_space' AND column_name = 'code_alive');
SET @stmt := IF(@has_code_alive = 0,
    'ALTER TABLE sys_space ADD COLUMN code_alive VARCHAR(64) AS (IF(is_deleted = 0, code, NULL)) STORED',
    'DO 0');
PREPARE space_stmt FROM @stmt; EXECUTE space_stmt; DEALLOCATE PREPARE space_stmt;

-- 唯一键从 (tenant_id, code) 换成 (tenant_id, code_alive)
SET @has_old_code_idx := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_space'
      AND index_name = 'uk_space_tenant_code' AND column_name = 'code');
SET @stmt := IF(@has_old_code_idx > 0,
    'ALTER TABLE sys_space DROP INDEX uk_space_tenant_code, ADD UNIQUE KEY uk_space_tenant_code (tenant_id, code_alive)',
    'DO 0');
PREPARE space_stmt FROM @stmt; EXECUTE space_stmt; DEALLOCATE PREPARE space_stmt;

SET @has_primary_marker := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_space_relation' AND column_name = 'primary_marker');
SET @stmt := IF(@has_primary_marker = 0,
    'ALTER TABLE sys_space_relation ADD COLUMN primary_marker TINYINT AS (IF(is_primary = 1, 1, NULL)) STORED',
    'DO 0');
PREPARE space_stmt FROM @stmt; EXECUTE space_stmt; DEALLOCATE PREPARE space_stmt;

SET @has_primary_idx := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_space_relation'
      AND index_name = 'uk_space_relation_primary');
SET @stmt := IF(@has_primary_idx = 0,
    'ALTER TABLE sys_space_relation ADD UNIQUE KEY uk_space_relation_primary (tenant_id, target_type, target_id, primary_marker)',
    'DO 0');
PREPARE space_stmt FROM @stmt; EXECUTE space_stmt; DEALLOCATE PREPARE space_stmt;

-- ---------- 3) 字典：空间类型 / 关联对象类型 ----------
-- 用固定 ID（3 / 4）与 lib 内既有字典（1 系统状态、2 性别）连续，避免雪花 ID 让升级库与全新库不一致。
-- ⚠️ 必须按 **code** 判存在，不能按 id：平台管理员可能已自建过同名/同 id 的字典。
--     按 id 判会让 id 被别的字典占用时静默跳过 → dict_id=3 不存在、但下面的字典项照插 ⇒ 孤儿项（实测）。
-- 也**不用 INSERT IGNORE**：IGNORE 会把守卫之外的冲突（主键/唯一键）一并吞掉，掩盖真实失败。
INSERT INTO sys_dict (id, name, code, remark, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict),
       '空间类型', 'space_type', '楼栋/楼层/房间/办公室/仓库/宿舍等', 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE code = 'space_type');
INSERT INTO sys_dict (id, name, code, remark, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict),
       '空间关联对象类型', 'space_target_type', '部门/人员/设备/资产等可挂到空间上的业务对象',
       1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE code = 'space_target_type');

-- 字典项：按 (dict_id, value) 存在性守卫 —— 不用 IGNORE 是因为唯一键只在 code 上，
-- 重复插入 item 不会撞唯一键（没有该约束），只能显式判存在。
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '园区', 'park', 'processing', 1, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'park');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '楼栋', 'building', 'processing', 2, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'building');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '楼层', 'floor', 'processing', 3, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'floor');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '办公室', 'office', 'success', 4, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'office');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '仓库', 'warehouse', 'warning', 5, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'warehouse');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '宿舍', 'dormitory', 'default', 6, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'dormitory');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '会议室', 'meeting_room', 'default', 7, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'meeting_room');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '车间', 'workshop', 'default', 8, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'workshop');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '机房', 'server_room', 'error', 9, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'server_room');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_type'), '其他', 'other', 'default', 99, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.value = 'other');

INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '部门', 'dept', 'processing', 1, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'dept');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '人员', 'user', 'processing', 2, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'user');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '设备', 'device', 'success', 3, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'device');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '资产', 'asset', 'default', 4, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'asset');
INSERT INTO sys_dict_item (id, dict_id, label, value, color, sort, create_user, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_dict_item), (SELECT id FROM sys_dict WHERE code = 'space_target_type'), '其他', 'other', 'default', 99, 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.value = 'other');

-- ---------- 4) 菜单与权限码 ----------
-- 空间管理挂在「组织管理」（3001）下：空间与用户/部门/岗位同属基础主数据，
-- 单开顶级目录会让它看起来像某个业务域的功能（IoT 的错觉正是要消除的）。
--
-- ⚠️ 判存在按 **auth_code**，不按 id：id 可能已被无关菜单占用（实测形态：id=2410 被占 ⇒
--    原写法静默跳过插入、却仍给 2410 授权 ⇒ 把 system:space:list 白授给了别人的菜单）。
-- 按钮的 pid 用 (SELECT id FROM sys_menu WHERE auth_code='...') 反查父页真实 id。
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu), 3001,
       'SystemSpace', 'menu', 0, '/system/space', '/system/space/list',
       'system:space:list', 'system.space.title', 'carbon:location', 5, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:list');

INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceAdd', 'button', 0, 'system:space:add', 'common.create', 1, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:add');
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceEdit', 'button', 0, 'system:space:edit', 'common.edit', 2, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:edit');
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceDelete', 'button', 0, 'system:space:delete', 'common.delete', 3, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:delete');
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
SELECT (SELECT IFNULL(MAX(id), 0) + 1 FROM sys_menu),
       (SELECT id FROM sys_menu WHERE auth_code = 'system:space:list'),
       'SystemSpaceBind', 'button', 0, 'system:space:bind', 'system.space.bind', 4, NOW(), 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE auth_code = 'system:space:bind');

-- ---------- 5) 授权 ----------
-- platform_only=0 的菜单不会被 002-data.sql 尾部那条「只授 platform_only=1」覆盖，必须显式双授：
--   sys_role_menu     → 平台管理员在菜单树里看得到
--   sys_template_menu → 租户可授菜单（新建/改角色时能被勾选）
-- 缺任一张的表现不同：缺 role 表是管理员看不到，缺 template 表是租户侧保存角色时报
-- 「角色授权包含租户权限模板之外的菜单」。
--
-- ⚠️ menu_id 一律按 **auth_code 反查真实 id**，不写死 2410/241xxx：
--    菜单 id 被占用时插入会被守卫跳过，写死 id 就会把权限白授给别人的菜单（实测）。
-- 用 INSERT ... SELECT WHERE NOT EXISTS（去掉 INSERT IGNORE）：守卫列就是主键两列，
-- IGNORE 在此只是冗余，而它会掩盖守卫之外的冲突。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                      'system:space:delete', 'system:space:bind')
  AND m.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                      'system:space:delete', 'system:space:bind')
  AND m.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_template_menu tm WHERE tm.template_id = 1 AND tm.menu_id = m.id);

-- ---------- 6) 自检：漏装即失败（不静默） ----------
-- 迁移脚本最大的风险是「报了成功、实际没装上」。这里做最小完备性断言：
-- 空间类型字典项必须 >= 10、空间菜单必须 = 5。任一不成立直接报错终止。
-- （用 SIGNAL 而不是 SELECT 输出，是为了让 mysql 客户端以非零退出码结束，脚本能停。）

SET @space_menu_cnt := (SELECT COUNT(*) FROM sys_menu
    WHERE auth_code IN ('system:space:list', 'system:space:add', 'system:space:edit',
                        'system:space:delete', 'system:space:bind') AND is_deleted = 0);
SET @space_type_cnt := (SELECT COUNT(*) FROM sys_dict_item i
    INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_type' AND i.is_deleted = 0);
SET @space_target_cnt := (SELECT COUNT(*) FROM sys_dict_item i
    INNER JOIN sys_dict d ON d.id = i.dict_id WHERE d.code = 'space_target_type' AND i.is_deleted = 0);

-- 5 个菜单；空间类型 >= 10（允许管理员后续自行增删，所以是下界）；关联类型 >= 5
SET @msg := CONCAT('空间迁移自检失败：menus=', @space_menu_cnt,
                   ' space_type_items=', @space_type_cnt,
                   ' target_type_items=', @space_target_cnt,
                   '（期望 menus=5、space_type_items>=10、target_type_items>=5）');
SET @fail := (@space_menu_cnt <> 5 OR @space_type_cnt < 10 OR @space_target_cnt < 5);
-- 用动态 SQL 触发 SIGNAL：MySQL 的 SIGNAL 不能在普通 IF 里带变量文案，故拼语句
SET @stmt := IF(@fail,
    CONCAT("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '", REPLACE(@msg, '''', ''), "'"),
    'DO 0');
PREPARE check_space FROM @stmt;
EXECUTE check_space;
DEALLOCATE PREPARE check_space;
