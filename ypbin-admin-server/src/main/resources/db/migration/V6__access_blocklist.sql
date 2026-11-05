-- =============================================================
-- 访问黑名单（Flyway V6，2026-11-04）
--
-- 能力：按 IP/CIDR、User-Agent 关键字、路径前缀拦截恶意扫描/爬虫/暴力破解；
-- 规则由管理端维护，写库后全量下发到 Redis（无 Redis 时退化为进程内存储），
-- 由 starter 的 Servlet 过滤器（单体）或网关过滤器（微服务）执行。
--
-- 说明：sys_access_blocklist 是平台级全局表，不参与租户隔离（见 application.yml 的
-- tenant.ignore-tables）；若遗漏该配置，租户上下文缺失时查询会直接失败。
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
    KEY idx_match (match_type, match_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '访问黑名单';

-- 安全防护菜单（平台专用）：目录 + 黑名单页面 + 按钮权限
INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort,
                             create_time, status, is_deleted)
VALUES (3009, 0, 'SecurityManage', 'catalog', 1, '/system/security', 'BasicLayout',
        'system.blocklist.title', 'carbon:security-services', 7, NOW(), 1, 0);

INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, path, component, auth_code, title, icon,
                             sort, create_time, status, is_deleted)
VALUES (3200, 3009, 'SystemAccessBlocklist', 'menu', 1, '/system/security/blocklist',
        '/system/security/blocklist/index', 'system:blocklist:list', 'system.blocklist.list',
        'carbon:firewall', 1, NOW(), 1, 0);

INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                             create_time, status, is_deleted)
VALUES (320001, 3200, 'SystemAccessBlocklistAdd', 'button', 1, 'system:blocklist:add',
        'common.create', 1, NOW(), 1, 0),
       (320002, 3200, 'SystemAccessBlocklistEdit', 'button', 1, 'system:blocklist:edit',
        'common.edit', 2, NOW(), 1, 0),
       (320003, 3200, 'SystemAccessBlocklistDelete', 'button', 1, 'system:blocklist:delete',
        'common.delete', 3, NOW(), 1, 0),
       (320004, 3200, 'SystemAccessBlocklistUnban', 'button', 1, 'system:blocklist:unban',
        'system.blocklist.unban', 4, NOW(), 1, 0);

-- 平台超级管理员获得新增菜单（V2 的全量授权只覆盖当时已存在的菜单）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 3009),
       (1, 3200),
       (1, 320001),
       (1, 320002),
       (1, 320003),
       (1, 320004);
