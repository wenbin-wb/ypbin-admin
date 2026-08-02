-- =============================================================
-- 种子数据回填创建人：将种子数据的 create_user 统一补为超级管理员（用户 id=1）
-- 使创建人名（@RefText 派生字段）在前端列表可见
-- =============================================================

UPDATE sys_tenant SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_dept SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_user SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_role SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_dict SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_dict_item SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_config SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_client SET create_user = 1 WHERE create_user IS NULL;
UPDATE sys_job SET create_user = 1 WHERE create_user IS NULL;
