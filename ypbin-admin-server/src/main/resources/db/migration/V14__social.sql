-- =============================================================
-- 用户-第三方平台绑定，全局表
-- =============================================================

CREATE TABLE sys_user_social
(
    id          BIGINT       NOT NULL COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    platform    VARCHAR(32)  NOT NULL COMMENT '平台标识',
    open_id     VARCHAR(128) NOT NULL COMMENT '第三方 openId',
    union_id    VARCHAR(128) NULL COMMENT '第三方 unionId',
    nickname    VARCHAR(64)  NULL COMMENT '第三方昵称',
    avatar      VARCHAR(512) NULL COMMENT '第三方头像',
    access_token VARCHAR(512) NULL COMMENT '第三方 accessToken',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    UNIQUE KEY uk_platform_open (platform, open_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户-第三方平台绑定';
