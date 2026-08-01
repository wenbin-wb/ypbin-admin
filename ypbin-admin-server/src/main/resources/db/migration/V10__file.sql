-- =============================================================
-- 文件管理，全局表
-- =============================================================

CREATE TABLE sys_file
(
    id            BIGINT       NOT NULL COMMENT '主键',
    platform      VARCHAR(32)  NULL COMMENT '存储平台',
    url           VARCHAR(512) NULL COMMENT '文件 URL',
    original_name VARCHAR(255) NULL COMMENT '原始文件名',
    file_name     VARCHAR(255) NULL COMMENT '存储文件名',
    file_path     VARCHAR(512) NULL COMMENT '文件路径',
    file_size     BIGINT       NULL COMMENT '文件大小（字节）',
    content_type  VARCHAR(128) NULL COMMENT 'MIME 类型',
    extension     VARCHAR(32)  NULL COMMENT '文件扩展名',
    hash          VARCHAR(128) NULL COMMENT '文件哈希',
    upload_user_id BIGINT      NULL COMMENT '上传人',
    module        VARCHAR(32)  NULL COMMENT '所属业务模块',
    create_user   BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NULL COMMENT '创建时间',
    update_user   BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NULL COMMENT '更新时间',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文件管理';

-- 文件管理菜单
INSERT INTO sys_menu (id, pid, name, type, path, component, auth_code, title, icon, sort, create_time, status, is_deleted)
VALUES (2600, 2, 'SystemFile', 'menu', '/system/file', '/system/file/list', 'system:file:list', 'system.file.title', 'carbon:document-attachment', 11, NOW(), 1, 0);
INSERT INTO sys_menu (id, pid, name, type, auth_code, title, sort, create_time, status, is_deleted)
VALUES (260001, 2600, 'SystemFileUpload', 'button', 'system:file:upload', 'common.upload', 1, NOW(), 1, 0),
       (260002, 2600, 'SystemFileDelete', 'button', 'system:file:delete', 'common.delete', 2, NOW(), 1, 0);
