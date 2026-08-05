-- =============================================================
-- ypbin-admin 商业授权模块建表 + 菜单种子（阶段二：签发管理控制台）
-- sys_license 为全局表（不隔离租户），已在 application.yml 的 tenant.ignore-tables 登记。
-- 授权本身可绑定某业务租户（tenant_id 字段），与表级租户隔离无关。
-- JSON 列（fingerprints/modules/quotas/attributes）由 MyBatis-Plus JacksonTypeHandler 读写。
-- =============================================================

-- 商业授权表
CREATE TABLE sys_license
(
    id             BIGINT       NOT NULL COMMENT '主键',
    license_id     VARCHAR(64)  NULL COMMENT '授权编号（签发时生成，全局唯一，用于联机校验与吊销）',
    subject        VARCHAR(128) NOT NULL COMMENT '被授权方名称',
    remark         VARCHAR(255) NULL COMMENT '供应方备注',
    fingerprints   JSON         NULL COMMENT '允许运行的机器指纹列表（为空表示不限机器）',
    tenant_id      VARCHAR(64)  NULL COMMENT '绑定租户标识（为空表示不限租户）',
    effective_at   DATETIME     NULL COMMENT '生效时间（早于此时间视为未生效）',
    expire_at      DATETIME     NULL COMMENT '到期时间（为空表示永久授权）',
    grace_days     INT          NULL DEFAULT 0 COMMENT '过期后的宽限天数（此期间状态为非法可用）',
    modules        JSON         NULL COMMENT '授权的功能模块标识集合（为空表示不做模块级限制）',
    quotas         JSON         NULL COMMENT '业务额度限制（如 device=100、user=500）',
    attributes     JSON         NULL COMMENT '自定义扩展参数',
    delivery_mode  VARCHAR(16)  NOT NULL COMMENT '交付模式：CODE 内联授权码 / FILE 授权文件',
    auth_code      MEDIUMTEXT   NULL COMMENT '签发产物：Base64 授权串（审批通过后写入）',
    approve_status VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '审批状态：DRAFT/PENDING/ISSUED/REJECTED/REVOKED',
    approve_user   BIGINT       NULL COMMENT '审批人（签发/驳回操作人，须不同于创建人）',
    approve_time   DATETIME     NULL COMMENT '审批时间',
    reject_reason  VARCHAR(255) NULL COMMENT '驳回原因',
    create_user    BIGINT       NULL COMMENT '创建人',
    create_time    DATETIME     NULL COMMENT '创建时间',
    update_user    BIGINT       NULL COMMENT '更新人',
    update_time    DATETIME     NULL COMMENT '更新时间',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_license_id (license_id),
    KEY idx_approve_status (approve_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商业授权';

-- =============================================================
-- 授权管理菜单（独立顶级目录 LicenseManage，id 3008，排在系统管理之后）
-- =============================================================
INSERT INTO sys_menu (id, pid, name, type, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (3008, 0, 'LicenseManage', 'catalog', '/system/license-manage', 'BasicLayout', 'system.license.title', 'carbon:license', 9, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (3100, 3008, 'SystemLicense', 'menu', '/system/license', '/system/license/list', 'system:license:list', 'system.license.list', 'carbon:license', 1, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (310001, 3100, 'SystemLicenseAdd', 'button', 'system:license:add', 'common.create', 1, NOW(), 1, 0),
       (310002, 3100, 'SystemLicenseEdit', 'button', 'system:license:edit', 'common.edit', 2, NOW(), 1, 0),
       (310003, 3100, 'SystemLicenseDelete', 'button', 'system:license:delete', 'common.delete', 3, NOW(), 1, 0),
       (310004, 3100, 'SystemLicenseSubmit', 'button', 'system:license:submit', 'system.license.submit', 4, NOW(), 1, 0),
       (310005, 3100, 'SystemLicenseApprove', 'button', 'system:license:approve', 'system.license.approve', 5, NOW(), 1, 0),
       (310006, 3100, 'SystemLicenseRevoke', 'button', 'system:license:revoke', 'system.license.revoke', 6, NOW(), 1, 0),
       (310007, 3100, 'SystemLicenseGenKey', 'button', 'system:license:genkey', 'system.license.genkey', 7, NOW(), 1, 0);

-- 全部权限模板追加授权本模块新增菜单（保持默认租户拥有全部菜单权限）
INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (3008, 3100, 310001, 310002, 310003, 310004, 310005, 310006, 310007);
