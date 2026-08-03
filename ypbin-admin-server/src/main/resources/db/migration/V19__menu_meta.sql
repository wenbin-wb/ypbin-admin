-- =============================================================
-- 菜单表补充路由 meta 高级字段，与前端路由能力对齐
-- =============================================================

ALTER TABLE sys_menu
    ADD COLUMN active_path          VARCHAR(255) NULL COMMENT '高亮的菜单路径' AFTER link,
    ADD COLUMN affix_tab            TINYINT      NULL COMMENT '是否固定标签页：1 是 0 否' AFTER active_path,
    ADD COLUMN badge                VARCHAR(64)  NULL COMMENT '徽标内容' AFTER affix_tab,
    ADD COLUMN badge_type           VARCHAR(16)  NULL COMMENT '徽标类型：dot 点 normal 文字' AFTER badge,
    ADD COLUMN badge_variants       VARCHAR(16)  NULL COMMENT '徽标样式' AFTER badge_type,
    ADD COLUMN hide_children_in_menu TINYINT     NULL COMMENT '是否隐藏子菜单：1 是 0 否' AFTER badge_variants,
    ADD COLUMN hide_in_breadcrumb   TINYINT      NULL COMMENT '是否在面包屑中隐藏：1 是 0 否' AFTER hide_children_in_menu,
    ADD COLUMN hide_in_tab          TINYINT      NULL COMMENT '是否在标签栏中隐藏：1 是 0 否' AFTER hide_in_breadcrumb;

-- 移除失效的定时任务日志独立菜单：日志改为任务列表内"查看日志"抽屉（按 jobId 分页），
-- 原 /system/job/log 无对应页面组件会 404。
DELETE FROM sys_menu WHERE id = 2951;
