-- AI 模块修复：补索引、用量/模板权限码、角色授权（幂等，可重复执行）
-- 索引：消息按会话查询、文档按知识库查询、用量按租户+时间统计、会话按用户排序
CREATE INDEX idx_ai_message_conv_time ON ai_message (conversation_id, create_time);
CREATE INDEX idx_ai_document_kb ON ai_document (knowledge_base_id);
CREATE INDEX idx_ai_usage_tenant_time ON ai_usage_log (tenant_id, create_time);
CREATE INDEX idx_ai_conversation_user_time ON ai_conversation (user_id, update_time);

-- 用量统计与 Prompt 模板按钮权限码（复用 V3 的菜单段 5040/5050）
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES
(5041, 5040, 'AiPromptList',   'button', 0, '', '', 'page.ai.prompt.list',   'carbon:template',   1, NOW(), 1, 0),
(5042, 5040, 'AiPromptCreate', 'button', 0, '', '', 'page.ai.prompt.create', 'carbon:template',   2, NOW(), 1, 0),
(5043, 5040, 'AiPromptEdit',   'button', 0, '', '', 'page.ai.prompt.edit',   'carbon:template',   3, NOW(), 1, 0),
(5044, 5040, 'AiPromptDelete', 'button', 0, '', '', 'page.ai.prompt.delete', 'carbon:template',   4, NOW(), 1, 0),
(5051, 5050, 'AiUsageView',    'button', 1, '', '', 'page.ai.usage.view',    'carbon:analytics',  1, NOW(), 1, 0)
ON DUPLICATE KEY UPDATE title = VALUES(title);

-- 非平台按钮权限同步到全部权限模板（与 V3 的 sys_template_menu 追加逻辑一致）
INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (5041, 5042, 5043, 5044) AND NOT EXISTS (
    SELECT 1 FROM sys_template_menu t WHERE t.template_id = 1 AND t.menu_id = sys_menu.id
);

-- 平台级用量查看权限授予平台超级管理员（与 V3 的 sys_role_menu 追加逻辑一致）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 5051 AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu r WHERE r.role_id = 1 AND r.menu_id = sys_menu.id
);
