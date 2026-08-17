-- V12: AI Wiki 阅读页菜单入口
-- @author wenbin
-- @since 2026-08-17

-- Wiki 阅读页菜单
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (5007, 5000, 'AiWiki', 'menu', 0, '/ai/wiki', '/ai/wiki/index', 'page.ai.wiki.title', 'carbon:document', 6, NOW(), 1, 0);

-- Wiki 阅读权限按钮（复用 ai:knowledge:list）
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES (5071, 5007, 'AiWikiView', 'button', 0, 'ai:knowledge:list', 'page.ai.wiki.view', 1, NOW(), 1, 0);
