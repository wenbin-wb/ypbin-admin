-- =============================================================
-- 访问黑名单（2026-11-05）
--
-- 平台级全局表，不参与租户隔离：封禁对象是 IP，按租户隔离会
-- 导致同一 IP 在 A 租户被封、B 租户放行，而 starter 的
-- BlocklistStore 没有租户维度。
-- 表结构、菜单权限点与部署脚本 001/002 保持一致。
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_access_blocklist
(
    id          BIGINT       NOT NULL COMMENT '主键',
    match_type  VARCHAR(32)  NOT NULL COMMENT '匹配维度：IP/CIDR/USER_AGENT/PATH',
    match_value VARCHAR(255) NOT NULL COMMENT '匹配值',
    reason      VARCHAR(255) NULL COMMENT '命中原因',
    expire_time DATETIME     NULL COMMENT '到期时间；为空表示长期有效',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_type_value (match_type, match_value),
    KEY idx_status_expire (status, expire_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '访问黑名单规则';

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort,
                      create_time, status, is_deleted)
SELECT 6000, 0, 'SecurityManage', 'catalog', 1, '/system/security', 'BasicLayout',
       'system.blocklist.title', 'carbon:security-services', 8, NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6000);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon,
                      sort, create_time, status, is_deleted)
SELECT 6001, 6000, 'SystemAccessBlocklist', 'menu', 1, '/system/security/blocklist',
       '/system/security/blocklist/index', 'system:blocklist:list', 'system.blocklist.list',
       'carbon:firewall', 1, NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6001);

INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                      create_time, status, is_deleted)
SELECT 600101, 6001, 'SystemAccessBlocklistAdd', 'button', 1, 'system:blocklist:add',
       'common.create', 1, NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 600101);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                      create_time, status, is_deleted)
SELECT 600102, 6001, 'SystemAccessBlocklistEdit', 'button', 1, 'system:blocklist:edit',
       'common.edit', 2, NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 600102);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                      create_time, status, is_deleted)
SELECT 600103, 6001, 'SystemAccessBlocklistDelete', 'button', 1, 'system:blocklist:delete',
       'common.delete', 3, NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 600103);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                      create_time, status, is_deleted)
SELECT 600104, 6001, 'SystemAccessBlocklistBan', 'button', 1, 'system:blocklist:ban',
       'system.blocklist.ban', 4, NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 600104);
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                      create_time, status, is_deleted)
SELECT 600105, 6001, 'SystemAccessBlocklistUnban', 'button', 1, 'system:blocklist:unban',
       'system.blocklist.unban', 5, NOW(), 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 600105);

-- 平台超级管理员获得新增菜单（已授权则忽略）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 6000), (1, 6001), (1, 600101), (1, 600102), (1, 600103), (1, 600104), (1, 600105);
