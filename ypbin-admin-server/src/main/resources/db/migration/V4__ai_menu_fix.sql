-- 修复 AI 菜单 title：旧数据存的是 "ai.chat.title"（无 page. 前缀），
-- 前端菜单渲染 $t(item.name) 查不到导致显示原始 key。此处幂等修正。
UPDATE sys_menu
SET title = REPLACE(title, 'ai.', 'page.ai.')
WHERE id BETWEEN 5000 AND 5099
  AND title LIKE 'ai.%';

-- 按钮权限 title 同步修正
UPDATE sys_menu
SET title = CONCAT('page.', title)
WHERE id BETWEEN 5000 AND 5099
  AND type = 'button'
  AND title LIKE 'ai:%';
