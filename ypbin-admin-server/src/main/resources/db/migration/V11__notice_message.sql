-- =============================================================
-- 公告 + 用户消息（站内信），公告全局、消息租户隔离
-- =============================================================

CREATE TABLE sys_notice
(
    id          BIGINT       NOT NULL COMMENT '主键',
    title       VARCHAR(255) NOT NULL COMMENT '标题',
    content     TEXT         NULL COMMENT '公告内容',
    notice_type TINYINT      NULL COMMENT '类型：1 通知 2 公告',
    publish_time DATETIME    NULL COMMENT '发布时间',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '系统公告';

CREATE TABLE sys_message
(
    id               BIGINT       NOT NULL COMMENT '主键',
    tenant_id        BIGINT       NULL COMMENT '租户 ID',
    receiver_user_id BIGINT       NOT NULL COMMENT '接收人用户 ID',
    title            VARCHAR(255) NOT NULL COMMENT '消息标题',
    content          TEXT         NULL COMMENT '消息内容',
    message_type     TINYINT      NULL COMMENT '类型：1 系统通知 2 用户消息',
    read_status      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读 1 已读',
    create_user      BIGINT       NULL COMMENT '创建人',
    create_time      DATETIME     NULL COMMENT '创建时间',
    update_user      BIGINT       NULL COMMENT '更新人',
    update_time      DATETIME     NULL COMMENT '更新时间',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_receiver (receiver_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户消息';

-- 公告管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2700, 2, 'SystemNotice', 'menu', '/system/notice', '/system/notice/list', 'system:notice:list', 'system.notice.title', 'carbon:notification', 12, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (270001, 2700, 'SystemNoticeAdd', 'button', 'system:notice:add', 'common.create', 1, NOW(), 1, 0),
       (270002, 2700, 'SystemNoticeEdit', 'button', 'system:notice:edit', 'common.edit', 2, NOW(), 1, 0),
       (270003, 2700, 'SystemNoticeDelete', 'button', 'system:notice:delete', 'common.delete', 3, NOW(), 1, 0);
