-- =============================================================
-- 定时任务管理 + 执行日志，全局表
-- =============================================================

CREATE TABLE sys_job
(
    id                BIGINT       NOT NULL COMMENT '主键',
    name              VARCHAR(128) NOT NULL COMMENT '任务名称',
    executor          VARCHAR(64)  NOT NULL COMMENT '执行器名称',
    cron              VARCHAR(64)  NULL COMMENT 'cron 表达式',
    fixed_rate_seconds BIGINT      NULL COMMENT '固定间隔秒数',
    args              VARCHAR(512) NULL COMMENT '执行参数',
    timeout_seconds   BIGINT       NULL DEFAULT 0 COMMENT '执行超时秒数',
    concurrent_guard  TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用集群防重',
    create_user       BIGINT       NULL COMMENT '创建人',
    create_time       DATETIME     NULL COMMENT '创建时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：1 启用 0 停用',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_executor (executor)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '定时任务';

CREATE TABLE sys_job_log
(
    id           BIGINT       NOT NULL COMMENT '主键',
    job_id       BIGINT       NOT NULL COMMENT '任务 ID',
    job_name     VARCHAR(128) NULL COMMENT '任务名称',
    executor     VARCHAR(64)  NULL COMMENT '执行器名称',
    trigger_time DATETIME     NULL COMMENT '触发时间',
    manual       TINYINT      NULL COMMENT '是否手动触发',
    outcome      TINYINT      NULL COMMENT '结果：0 跳过 1 成功 2 失败',
    duration_ms  BIGINT       NULL COMMENT '执行耗时（毫秒）',
    error_msg    TEXT         NULL COMMENT '错误信息',
    create_user  BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NULL COMMENT '创建时间',
    update_user  BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NULL COMMENT '更新时间',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_job_id (job_id),
    KEY idx_trigger_time (trigger_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '定时任务执行日志';

-- 定时任务管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2800, 2, 'SystemJob', 'menu', '/system/job', '/system/job/list', 'system:job:list', 'system.job.title', 'carbon:timer', 13, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (280001, 2800, 'SystemJobAdd', 'button', 'system:job:add', 'common.create', 1, NOW(), 1, 0),
       (280002, 2800, 'SystemJobEdit', 'button', 'system:job:edit', 'common.edit', 2, NOW(), 1, 0),
       (280003, 2800, 'SystemJobDelete', 'button', 'system:job:delete', 'common.delete', 3, NOW(), 1, 0);

-- 示例任务：清理临时文件（默认停用）
INSERT INTO sys_job (id, name, executor, cron, create_time, status, is_deleted)
VALUES (1, '清理临时文件', 'cleanTempFile', '0 0 3 * * ?', NOW(), 0, 0);
