-- =============================================================
-- ypbin-admin 商业授权模块建表（阶段二：签发管理控制台）
-- sys_license 为全局表（不隔离租户），已在 application.yml 的 tenant.ignore-tables 登记。
-- 授权本身可绑定某业务租户（tenant_id 字段），与表级租户隔离无关。
-- JSON 列（fingerprints/modules/quotas/attributes）由 MyBatis-Plus JacksonTypeHandler 读写。
-- 授权管理菜单种子已并入 V2__data.sql（LicenseManage 3008 / SystemLicense 3100 / 按钮 310001~310007）。
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
    app_id         BIGINT       NULL COMMENT '联机开放应用ID（签发时按被授权方自动创建或复用）',
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
    KEY idx_approve_status (approve_status),
    KEY idx_app_id (app_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商业授权';
