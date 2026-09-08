-- =============================================================
-- ypbin-admin 小程序（牌账清 CardTab & 家庭菜单 FamilyMenu）建表脚本
-- 遵循 BaseEntity 规范：id / create_user / create_time / update_user / update_time / status / is_deleted
-- =============================================================

-- -------------------------------------------------------------
-- 1. 牌账清 (CardTab) 模块
-- -------------------------------------------------------------

-- 房间表
CREATE TABLE IF NOT EXISTS cardtab_room
(
    id                  BIGINT       NOT NULL COMMENT '主键',
    owner_user_id       BIGINT       NOT NULL COMMENT '房主用户 ID',
    name                VARCHAR(64)  NOT NULL COMMENT '房间名称',
    room_code           VARCHAR(16)  NOT NULL COMMENT '房间口令码（4-6位）',
    game_type           VARCHAR(32)  NOT NULL DEFAULT 'MAHJONG' COMMENT '玩法类型：MAHJONG/DOUDUIZHU/RUNFAST/CUSTOM',
    unit                VARCHAR(16)  NOT NULL DEFAULT '积分' COMMENT '计分单位',
    bookkeeping_mode    VARCHAR(32)  NOT NULL DEFAULT 'SELF_PAY_TO_MEMBER' COMMENT '记账模式',
    allow_passcode_join TINYINT      NOT NULL DEFAULT 1 COMMENT '是否允许口令加入：1 允许 0 禁止',
    room_status         VARCHAR(16)  NOT NULL DEFAULT 'ONGOING' COMMENT '房间状态：ONGOING 进行中 SETTLED 已结算 CLOSED 已关闭',
    settled_at          DATETIME     NULL COMMENT '结算时间',
    create_user         BIGINT       NULL COMMENT '创建人',
    create_time         DATETIME     NULL COMMENT '创建时间',
    update_user         BIGINT       NULL COMMENT '更新人',
    update_time         DATETIME     NULL COMMENT '更新时间',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_room_code (room_code),
    KEY idx_owner_user_id (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '牌账清-房间表';

-- 房间成员表
CREATE TABLE IF NOT EXISTS cardtab_room_member
(
    id            BIGINT         NOT NULL COMMENT '主键',
    room_id       BIGINT         NOT NULL COMMENT '房间 ID',
    user_id       BIGINT         NOT NULL COMMENT '用户 ID',
    nickname      VARCHAR(64)    NOT NULL COMMENT '房间内昵称',
    avatar_url    VARCHAR(255)   NULL COMMENT '头像地址',
    avatar_text   VARCHAR(8)     NULL COMMENT '头像文字',
    role          VARCHAR(16)    NOT NULL DEFAULT 'MEMBER' COMMENT '成员角色：OWNER 房主 MEMBER 成员',
    member_status VARCHAR(16)    NOT NULL DEFAULT 'IN_ROOM' COMMENT '成员状态：IN_ROOM 在房 LEFT 已退出',
    total_score   DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '当前积分累计',
    joined_at     DATETIME       NULL COMMENT '加入时间',
    left_at       DATETIME       NULL COMMENT '退出时间',
    create_user   BIGINT         NULL COMMENT '创建人',
    create_time   DATETIME       NULL COMMENT '创建时间',
    update_user   BIGINT         NULL COMMENT '更新人',
    update_time   DATETIME       NULL COMMENT '更新时间',
    status        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted    TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_user (room_id, user_id, is_deleted),
    KEY idx_room_id (room_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '牌账清-房间成员表';

-- 支出记录表
CREATE TABLE IF NOT EXISTS cardtab_expense
(
    id             BIGINT         NOT NULL COMMENT '主键',
    room_id        BIGINT         NOT NULL COMMENT '房间 ID',
    from_member_id BIGINT         NOT NULL COMMENT '支出成员 ID',
    to_member_id   BIGINT         NOT NULL COMMENT '收款成员 ID',
    amount         DECIMAL(10, 2) NOT NULL COMMENT '金额/积分',
    note           VARCHAR(255)   NULL COMMENT '备注说明',
    expense_status VARCHAR(16)    NOT NULL DEFAULT 'NORMAL' COMMENT '支出状态：NORMAL 正常 REVOKED 已撤销',
    revoked_at     DATETIME       NULL COMMENT '撤销时间',
    create_user    BIGINT         NULL COMMENT '创建人',
    create_time    DATETIME       NULL COMMENT '创建时间',
    update_user    BIGINT         NULL COMMENT '更新人',
    update_time    DATETIME       NULL COMMENT '更新时间',
    status         TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted     TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_room_id (room_id),
    KEY idx_from_member (from_member_id),
    KEY idx_to_member (to_member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '牌账清-支出记录表';

-- 房间流水事件表
CREATE TABLE IF NOT EXISTS cardtab_room_event
(
    id               BIGINT         NOT NULL COMMENT '主键',
    room_id          BIGINT         NOT NULL COMMENT '房间 ID',
    event_type       VARCHAR(32)    NOT NULL COMMENT '事件类型：ROOM_CREATED/MEMBER_JOINED/MEMBER_LEFT/EXPENSE_CREATED/EXPENSE_REVOKED/ROOM_SETTLED',
    member_id        BIGINT         NULL COMMENT '关联成员 ID',
    ref_id           BIGINT         NULL COMMENT '关联记录 ID（如支出ID）',
    title            VARCHAR(64)    NOT NULL COMMENT '展示标题',
    content          VARCHAR(255)   NULL COMMENT '展示详细内容',
    amount           DECIMAL(10, 2) NULL COMMENT '涉及金额',
    from_member_name VARCHAR(64)    NULL COMMENT '支出方名称',
    to_member_name   VARCHAR(64)    NULL COMMENT '收款方名称',
    create_user      BIGINT         NULL COMMENT '创建人',
    create_time      DATETIME       NULL COMMENT '创建时间',
    update_user      BIGINT         NULL COMMENT '更新人',
    update_time      DATETIME       NULL COMMENT '更新时间',
    status           TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_room_id (room_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '牌账清-房间流水表';

-- 结算快照表
CREATE TABLE IF NOT EXISTS cardtab_settlement_snapshot
(
    id            BIGINT     NOT NULL COMMENT '主键',
    room_id       BIGINT     NOT NULL COMMENT '房间 ID',
    snapshot_json MEDIUMTEXT NOT NULL COMMENT '结算快照数据（排名、转账矩阵、分享文案等 JSON）',
    create_user   BIGINT     NULL COMMENT '创建人',
    create_time   DATETIME   NULL COMMENT '创建时间',
    update_user   BIGINT     NULL COMMENT '更新人',
    update_time   DATETIME   NULL COMMENT '更新时间',
    status        TINYINT    NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted    TINYINT    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_room_id (room_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '牌账清-结算快照表';


-- -------------------------------------------------------------
-- 2. 家庭菜单 (FamilyMenu) 模块
-- -------------------------------------------------------------

-- 家庭餐厅表
CREATE TABLE IF NOT EXISTS familymenu_room
(
    id            BIGINT       NOT NULL COMMENT '主键',
    owner_user_id BIGINT       NOT NULL COMMENT '创建人/主厨用户 ID',
    name          VARCHAR(64)  NOT NULL COMMENT '餐厅/家庭名称',
    invite_code   VARCHAR(16)  NOT NULL COMMENT '邀请码',
    create_user   BIGINT       NULL COMMENT '创建人',
    create_time   DATETIME     NULL COMMENT '创建时间',
    update_user   BIGINT       NULL COMMENT '更新人',
    update_time   DATETIME     NULL COMMENT '更新时间',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_invite_code (invite_code),
    KEY idx_owner_user (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '家庭菜单-家庭餐厅表';

-- 餐厅成员表
CREATE TABLE IF NOT EXISTS familymenu_member
(
    id          BIGINT       NOT NULL COMMENT '主键',
    room_id     BIGINT       NOT NULL COMMENT '餐厅 ID',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    nickname    VARCHAR(64)  NOT NULL COMMENT '成员称呼',
    avatar_url  VARCHAR(255) NULL COMMENT '成员头像',
    role        VARCHAR(16)  NOT NULL DEFAULT 'DINER' COMMENT '角色：CHEF 主厨 DINER 食客',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_user (room_id, user_id, is_deleted),
    KEY idx_room_id (room_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '家庭菜单-餐厅成员表';

-- 餐厅菜品库表
CREATE TABLE IF NOT EXISTS familymenu_dish
(
    id          BIGINT         NOT NULL COMMENT '主键',
    room_id     BIGINT         NOT NULL COMMENT '餐厅 ID',
    name        VARCHAR(64)    NOT NULL COMMENT '菜品名称',
    category    VARCHAR(32)    NOT NULL DEFAULT '热菜' COMMENT '菜品分类：热菜/凉菜/汤羹/主食/甜点小吃',
    image_url   VARCHAR(255)   NULL COMMENT '菜品图片',
    tags        VARCHAR(128)   NULL COMMENT '标签（逗号分隔）',
    price       DECIMAL(10, 2) NULL DEFAULT 0.00 COMMENT '参考费用',
    create_user BIGINT         NULL COMMENT '创建人',
    create_time DATETIME       NULL COMMENT '创建时间',
    update_user BIGINT         NULL COMMENT '更新人',
    update_time DATETIME       NULL COMMENT '更新时间',
    status      TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_room_category (room_id, category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '家庭菜单-餐厅菜品库';

-- 系统预设菜品表
CREATE TABLE IF NOT EXISTS familymenu_preset_dish
(
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '菜品名称',
    category    VARCHAR(32)  NOT NULL COMMENT '分类：热菜/凉菜/汤羹/主食/甜点小吃',
    image_url   VARCHAR(255) NULL COMMENT '预设图片',
    tags        VARCHAR(128) NULL COMMENT '标签',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '家庭菜单-预设菜品表';

-- 心愿单表
CREATE TABLE IF NOT EXISTS familymenu_wishlist
(
    id          BIGINT       NOT NULL COMMENT '主键',
    room_id     BIGINT       NOT NULL COMMENT '餐厅 ID',
    user_id     BIGINT       NOT NULL COMMENT '许愿用户 ID',
    dish_name   VARCHAR(64)  NOT NULL COMMENT '心愿菜品名',
    wish_status VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '心愿状态：PENDING 待接单 ACCEPTED 已接单 REJECTED 已婉拒',
    remark      VARCHAR(255) NULL COMMENT '备注说明',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_room_id (room_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '家庭菜单-心愿单表';

-- 就餐订单表
CREATE TABLE IF NOT EXISTS familymenu_order
(
    id             BIGINT         NOT NULL COMMENT '主键',
    room_id        BIGINT         NOT NULL COMMENT '餐厅 ID',
    order_date     DATE           NOT NULL COMMENT '就餐日期',
    meal_time      VARCHAR(16)    NOT NULL DEFAULT 'DINNER' COMMENT '就餐时段：BREAKFAST 早餐 LUNCH 午餐 DINNER 晚餐',
    order_status   VARCHAR(16)    NOT NULL DEFAULT 'PICKING' COMMENT '状态：PICKING 点餐中 WAITING 等待做饭 COOKING 烹饪中 DONE 已开饭',
    cost           DECIMAL(10, 2) NULL COMMENT '采购费用',
    remark         VARCHAR(500)   NULL COMMENT '采购备注',
    photo          VARCHAR(1000)  NULL COMMENT '采购照片凭证（多张逗号分隔）',
    cooking_remark VARCHAR(500)   NULL COMMENT '烹饪备注',
    cooking_photo  VARCHAR(1000)  NULL COMMENT '烹饪成品照片（多张逗号分隔）',
    create_user    BIGINT         NULL COMMENT '创建人',
    create_time    DATETIME       NULL COMMENT '创建时间',
    update_user    BIGINT         NULL COMMENT '更新人',
    update_time    DATETIME       NULL COMMENT '更新时间',
    status         TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted     TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_room_date (room_id, order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '家庭菜单-就餐订单表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS familymenu_order_detail
(
    id           BIGINT       NOT NULL COMMENT '主键',
    order_id     BIGINT       NOT NULL COMMENT '订单 ID',
    dish_id      BIGINT       NULL COMMENT '菜品 ID（若来自菜品库）',
    dish_name    VARCHAR(64)  NOT NULL COMMENT '菜品名称',
    dish_image   VARCHAR(255) NULL COMMENT '菜品图片',
    quantity     INT          NOT NULL DEFAULT 1 COMMENT '份数',
    requester_id BIGINT       NOT NULL COMMENT '点菜人用户 ID',
    create_user  BIGINT       NULL COMMENT '创建人',
    create_time  DATETIME     NULL COMMENT '创建时间',
    update_user  BIGINT       NULL COMMENT '更新人',
    update_time  DATETIME     NULL COMMENT '更新时间',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '家庭菜单-订单明细表';
