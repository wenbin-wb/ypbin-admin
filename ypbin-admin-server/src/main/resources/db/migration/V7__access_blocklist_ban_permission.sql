-- =============================================================
-- 访问黑名单：临时封禁按钮权限（V7，2026-11-05）
-- =============================================================

INSERT IGNORE INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort,
                             create_time, status, is_deleted)
VALUES (320005, 3200, 'SystemAccessBlocklistBan', 'button', 1, 'system:blocklist:ban',
        'system.blocklist.ban', 5, NOW(), 1, 0);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 320005);
