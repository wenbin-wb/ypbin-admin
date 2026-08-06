-- =============================================================
-- 授权双人复核测试账号
-- 授权签发要求「审批人 ≠ 创建人」，故内置第二个超管账号，与 admin 互为复核人：
-- 一方创建并提交草稿，另一方登录后审批签发。
-- 账号：approver / admin123（BCrypt 同 admin，仅本地测试用）。
-- 授予 super 角色（跳过权限码校验，天然拥有 license:add/submit/approve 等全部权限）。
-- =============================================================

INSERT INTO sys_user (id, tenant_id, username, password, real_name, nickname, dept_id, gender,
                      remark, pwd_reset_time, create_user, create_time, status, is_deleted)
VALUES (6, 1, 'approver', '$2a$10$ZuXfY6FkrI0fEGRoX9AlZuo3r/askEJEVHz6rKwKMrDVCpttLIq82',
        '授权审批员', '审批员', 1, 1, '授权双人复核的第二审批人', NOW(), 1, NOW(), 1, 0);

INSERT INTO sys_user_role (user_id, role_id) VALUES (6, 1);
