-- =============================================================
-- 系统日志（操作日志 + 登录日志），全局表
-- =============================================================

CREATE TABLE sys_log
(
    id              BIGINT       NOT NULL COMMENT '主键',
    description     VARCHAR(255) NULL COMMENT '日志描述',
    module          VARCHAR(64)  NULL COMMENT '所属模块',
    request_method  VARCHAR(10)  NULL COMMENT '请求方法',
    request_uri     VARCHAR(255) NULL COMMENT '请求 URI',
    request_param   TEXT         NULL COMMENT '请求参数',
    request_body    MEDIUMTEXT   NULL COMMENT '请求体',
    response_body   MEDIUMTEXT   NULL COMMENT '响应体',
    status_code     INT          NULL COMMENT 'HTTP 状态码',
    ip              VARCHAR(64)  NULL COMMENT '客户端 IP',
    location        VARCHAR(128) NULL COMMENT 'IP 归属地',
    browser         VARCHAR(128) NULL COMMENT '浏览器',
    os              VARCHAR(128) NULL COMMENT '操作系统',
    client_id       VARCHAR(64)  NULL COMMENT '登录客户端 ID',
    client_type     VARCHAR(16)  NULL COMMENT '客户端类型',
    auth_type       VARCHAR(16)  NULL COMMENT '认证方式',
    operate_user_id BIGINT       NULL COMMENT '操作人用户 ID',
    operate_time    DATETIME     NULL COMMENT '操作时间',
    time_taken      BIGINT       NULL COMMENT '耗时（毫秒）',
    success         TINYINT      NULL COMMENT '是否成功：1 是 0 否',
    error_msg       TEXT         NULL COMMENT '错误信息',
    create_user     BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_user     BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NULL COMMENT '更新时间',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_module (module),
    KEY idx_operate_user_id (operate_user_id),
    KEY idx_operate_time (operate_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统日志';

-- 日志查询菜单 + 按钮（只读，仅 list）
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (270, 2, 'SystemLog', 'menu', '/system/log', '/system/log/list', 'system:log:list', 'system.log.title', 'carbon:document', 7, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (27001, 270, 'SystemLogExport', 'button', 'system:log:export', 'common.export', 1, NOW(), 1, 0);
