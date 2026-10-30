-- =============================================================
-- ypbin-admin 小程序（牌账清 CardTab & 家庭菜单 FamilyMenu）单独脚本
-- 内容：建表 + 预设菜种子 + MINIAPP 客户端认证方式 + 旧库契约幂等对齐
-- 遵循 BaseEntity 规范：id / create_user / create_time / update_user / update_time / status / is_deleted
-- 状态：开发阶段合并为单文件；后续小程序增量迁移再从 V4 起编号。
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

-- 玩法模板表（归属创建者本人，非房间级数据）
CREATE TABLE IF NOT EXISTS cardtab_game_template
(
    id                  BIGINT      NOT NULL COMMENT '主键',
    owner_user_id       BIGINT      NOT NULL COMMENT '归属用户 ID（模板仅本人可见可改）',
    name                VARCHAR(64) NOT NULL COMMENT '模板名称',
    game_type           VARCHAR(32) NOT NULL DEFAULT 'MAHJONG' COMMENT '玩法类型：MAHJONG/DOUDUIZHU/RUNFAST/CUSTOM',
    unit                VARCHAR(16) NOT NULL DEFAULT '积分' COMMENT '计分单位',
    bookkeeping_mode    VARCHAR(32) NULL COMMENT '记账模式：SELF_PAY_TO_MEMBER/MULTI_MEMBER_RECORD',
    allow_passcode_join TINYINT     NOT NULL DEFAULT 1 COMMENT '是否允许口令加入：1 允许 0 禁止',
    create_user         BIGINT      NULL COMMENT '创建人',
    create_time         DATETIME    NULL COMMENT '创建时间',
    update_user         BIGINT      NULL COMMENT '更新人',
    update_time         DATETIME    NULL COMMENT '更新时间',
    status              TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_owner_user_id (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '牌账清-玩法模板表';

-- 意见反馈表
CREATE TABLE IF NOT EXISTS cardtab_feedback
(
    id              BIGINT        NOT NULL COMMENT '主键',
    user_id         BIGINT        NOT NULL COMMENT '提交用户 ID',
    content         VARCHAR(1000) NOT NULL COMMENT '反馈内容',
    contact         VARCHAR(64)   NULL COMMENT '联系方式（微信号等）',
    feedback_status VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING 待处理 PROCESSED 已处理',
    create_user     BIGINT        NULL COMMENT '创建人',
    create_time     DATETIME      NULL COMMENT '创建时间',
    update_user     BIGINT        NULL COMMENT '更新人',
    update_time     DATETIME      NULL COMMENT '更新时间',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 禁用',
    is_deleted      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '牌账清-意见反馈表';


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
    order_type     VARCHAR(16)    NOT NULL DEFAULT 'DINNER' COMMENT '就餐时段：BREAKFAST 早餐 LUNCH 午餐 DINNER 晚餐',
    order_status   VARCHAR(16)    NOT NULL DEFAULT 'ORDERING' COMMENT '状态：ORDERING 点单中 SHOPPING 买菜中 COOKING 烹饪中 DONE 已上桌（与小程序端状态机一致）',
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
-- =============================================================
-- FamilyMenu 预设系统菜谱初始化脚本
-- 共包含 100 道常见家常菜，涵盖热菜、凉菜、汤羹、主食、甜点小吃五大类。
-- =============================================================

INSERT INTO familymenu_preset_dish (id, name, category, image_url, tags, is_deleted, status) VALUES
(10001, '西红柿炒鸡蛋', '热菜', '', '家常,快手,素菜', 0, 1),
(10002, '酸辣土豆丝', '热菜', '', '家常,快手,微辣,素菜', 0, 1),
(10003, '鱼香肉丝', '热菜', '', '经典,下饭,微辣,荤菜', 0, 1),
(10004, '宫保鸡丁', '热菜', '', '经典,微辣,荤菜', 0, 1),
(10005, '红烧肉', '热菜', '', '经典,硬菜,荤菜,偏甜', 0, 1),
(10006, '糖醋排骨', '热菜', '', '经典,偏甜,硬菜,荤菜', 0, 1),
(10007, '麻婆豆腐', '热菜', '', '经典,麻辣,下饭,素菜', 0, 1),
(10008, '青椒肉丝', '热菜', '', '家常,快手,荤菜', 0, 1),
(10009, '回锅肉', '热菜', '', '经典,微辣,下饭,荤菜', 0, 1),
(10010, '蒜苔炒肉', '热菜', '', '家常,荤菜', 0, 1),
(10011, '红烧茄子', '热菜', '', '家常,下饭,素菜', 0, 1),
(10012, '地三鲜', '热菜', '', '经典,东北菜,素菜', 0, 1),
(10013, '干煸四季豆', '热菜', '', '家常,微辣,素菜', 0, 1),
(10014, '辣子鸡丁', '热菜', '', '经典,麻辣,荤菜', 0, 1),
(10015, '水煮肉片', '热菜', '', '经典,麻辣,硬菜,荤菜', 0, 1),
(10016, '清炒西兰花', '热菜', '', '清淡,健康,快手,素菜', 0, 1),
(10017, '蚝油生菜', '热菜', '', '清淡,快手,素菜', 0, 1),
(10018, '蒜蓉油麦菜', '热菜', '', '清淡,快手,素菜', 0, 1),
(10019, '葱爆羊肉', '热菜', '', '经典,荤菜,硬菜', 0, 1),
(10020, '木须肉', '热菜', '', '家常,荤菜', 0, 1),
(10021, '清蒸鲈鱼', '热菜', '', '清淡,海鲜,硬菜', 0, 1),
(10022, '红烧带鱼', '热菜', '', '家常,海鲜', 0, 1),
(10023, '油焖大虾', '热菜', '', '硬菜,海鲜,偏甜', 0, 1),
(10024, '蒜蓉粉丝蒸虾', '热菜', '', '清淡,海鲜,硬菜', 0, 1),
(10025, '爆炒腰花', '热菜', '', '家常,荤菜', 0, 1),
(10026, '芹菜炒香干', '热菜', '', '家常,快手,素菜', 0, 1),
(10027, '韭菜炒鸡蛋', '热菜', '', '家常,快手,素菜', 0, 1),
(10028, '番茄牛腩', '热菜', '', '经典,硬菜,炖菜', 0, 1),
(10029, '土豆炖牛肉', '热菜', '', '家常,硬菜,炖菜', 0, 1),
(10030, '黄焖鸡', '热菜', '', '下饭,荤菜,炖菜', 0, 1),
(10031, '红烧排骨', '热菜', '', '经典,硬菜,荤菜', 0, 1),
(10032, '干锅包菜', '热菜', '', '微辣,下饭,素菜', 0, 1),
(10033, '虎皮青椒', '热菜', '', '微辣,下饭,素菜', 0, 1),
(10034, '蒜蓉空心菜', '热菜', '', '清淡,快手,素菜', 0, 1),
(10035, '白菜炖豆腐', '热菜', '', '清淡,家常,炖菜,素菜', 0, 1),
(10036, '手撕包菜', '热菜', '', '微辣,快手,素菜', 0, 1),
(10037, '农家小炒肉', '热菜', '', '微辣,下饭,荤菜', 0, 1),
(10038, '酸菜鱼', '热菜', '', '经典,微辣,硬菜,海鲜', 0, 1),
(10039, '毛血旺', '热菜', '', '麻辣,硬菜,荤菜', 0, 1),
(10040, '红烧狮子头', '热菜', '', '经典,硬菜,荤菜', 0, 1),
(10041, '拍黄瓜', '凉菜', '', '清爽,快手,素菜', 0, 1),
(10042, '凉拌木耳', '凉菜', '', '清爽,健康,素菜', 0, 1),
(10043, '凉拌皮蛋', '凉菜', '', '下酒,家常', 0, 1),
(10044, '凉拌海带丝', '凉菜', '', '清爽,素菜', 0, 1),
(10045, '凉拌三丝', '凉菜', '', '清爽,素菜', 0, 1),
(10046, '口水鸡', '凉菜', '', '经典,麻辣,荤菜', 0, 1),
(10047, '夫妻肺片', '凉菜', '', '经典,麻辣,荤菜', 0, 1),
(10048, '凉拌猪耳', '凉菜', '', '下酒,荤菜', 0, 1),
(10049, '凉拌豆腐皮', '凉菜', '', '清爽,素菜', 0, 1),
(10050, '小葱拌豆腐', '凉菜', '', '清淡,素菜', 0, 1),
(10051, '蒜泥白肉', '凉菜', '', '微辣,荤菜', 0, 1),
(10052, '凉拌牛肉', '凉菜', '', '下酒,荤菜', 0, 1),
(10053, '泡椒凤爪', '凉菜', '', '微辣,下酒', 0, 1),
(10054, '凉拌金针菇', '凉菜', '', '微辣,素菜', 0, 1),
(10055, '皮蛋豆腐', '凉菜', '', '清淡,快手,素菜', 0, 1),
(10056, '凉拌藕片', '凉菜', '', '清爽,素菜', 0, 1),
(10057, '姜汁变蛋', '凉菜', '', '家常', 0, 1),
(10058, '麻酱油麦菜', '凉菜', '', '清爽,素菜', 0, 1),
(10059, '老醋花生', '凉菜', '', '下酒,素菜', 0, 1),
(10060, '盐水花生', '凉菜', '', '下酒,清淡', 0, 1),
(10061, '紫菜蛋花汤', '汤羹', '', '快手,清淡', 0, 1),
(10062, '西红柿鸡蛋汤', '汤羹', '', '快手,清淡', 0, 1),
(10063, '排骨冬瓜汤', '汤羹', '', '清淡,滋补', 0, 1),
(10064, '排骨莲藕汤', '汤羹', '', '经典,滋补', 0, 1),
(10065, '山药排骨汤', '汤羹', '', '健康,滋补', 0, 1),
(10066, '老母鸡汤', '汤羹', '', '经典,滋补,硬菜', 0, 1),
(10067, '鲫鱼豆腐汤', '汤羹', '', '清淡,滋补,海鲜', 0, 1),
(10068, '三鲜汤', '汤羹', '', '鲜美,清淡', 0, 1),
(10069, '酸辣汤', '汤羹', '', '微辣,开胃', 0, 1),
(10070, '玉米排骨汤', '汤羹', '', '清淡,偏甜', 0, 1),
(10071, '蘑菇肉片汤', '汤羹', '', '鲜美,快手', 0, 1),
(10072, '小白菜豆腐汤', '汤羹', '', '清淡,快手,素菜', 0, 1),
(10073, '萝卜牛腩汤', '汤羹', '', '滋补,荤菜', 0, 1),
(10074, '海带排骨汤', '汤羹', '', '健康,滋补', 0, 1),
(10075, '疙瘩汤', '汤羹', '', '家常,主食', 0, 1),
(10076, '蛋炒饭', '主食', '', '快手,家常', 0, 1),
(10077, '扬州炒饭', '主食', '', '经典,丰富', 0, 1),
(10078, '老干妈炒饭', '主食', '', '微辣,快手', 0, 1),
(10079, '肉丝炒面', '主食', '', '家常,面食', 0, 1),
(10080, '西红柿鸡蛋面', '主食', '', '快手,面食,清淡', 0, 1),
(10081, '炸酱面', '主食', '', '经典,面食', 0, 1),
(10082, '打卤面', '主食', '', '家常,面食', 0, 1),
(10083, '素汤面', '主食', '', '清淡,快手,面食', 0, 1),
(10084, '水饺', '主食', '', '经典,面食', 0, 1),
(10085, '煎饺', '主食', '', '香脆,面食', 0, 1),
(10086, '葱油饼', '主食', '', '面食,早餐', 0, 1),
(10087, '韭菜盒子', '主食', '', '面食,家常', 0, 1),
(10088, '红薯粥', '主食', '', '清淡,健康', 0, 1),
(10089, '皮蛋瘦肉粥', '主食', '', '经典,粥品', 0, 1),
(10090, '南瓜粥', '主食', '', '偏甜,健康', 0, 1),
(10091, '拔丝地瓜', '甜点小吃', '', '偏甜,小吃', 0, 1),
(10092, '炸牛奶', '甜点小吃', '', '偏甜,小吃', 0, 1),
(10093, '红糖糍粑', '甜点小吃', '', '偏甜,小吃', 0, 1),
(10094, '南瓜饼', '甜点小吃', '', '偏甜,小吃', 0, 1),
(10095, '炸薯条', '甜点小吃', '', '小吃,快手', 0, 1),
(10096, '炸鸡块', '甜点小吃', '', '小吃,肉类', 0, 1),
(10097, '银耳莲子羹', '甜点小吃', '', '偏甜,健康,滋补', 0, 1),
(10098, '绿豆汤', '甜点小吃', '', '清凉,消暑', 0, 1),
(10099, '水果沙拉', '甜点小吃', '', '清爽,健康', 0, 1),
(10100, '鸡蛋煎饼', '甜点小吃', '', '小吃,快手', 0, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), category=VALUES(category), tags=VALUES(tags);
-- =============================================================
-- 小程序登录认证方式注册
--
-- 小程序登录复用 web-admin 客户端（与服务端既有登录会话/会话策略一致），
-- 认证方式为 MINIAPP。DbLoginClientProvider 会按 sys_client.auth_types 校验认证方式，
-- 未登记 MINIAPP 时登录会被拒绝（客户端不支持当前认证方式）。
--
-- 幂等：仅在 web-admin 未包含 MINIAPP 时追加。
-- =============================================================

UPDATE sys_client
SET auth_types = CASE
        WHEN auth_types IS NULL OR auth_types = '' THEN 'MINIAPP'
        ELSE CONCAT(auth_types, ',MINIAPP')
    END
WHERE client_id = 'web-admin'
  AND (auth_types IS NULL OR auth_types NOT LIKE '%MINIAPP%');
-- =============================================================
-- 小程序端侧契约对齐（幂等）
--
-- 适用对象：已由旧版 deploy/sql/006 建表、尚未执行 2026-10-16 增量脚本的库。
-- 全新库的建表段已包含最终结构，本段对其无副作用。
--
-- 内容：
--   1) familymenu_order.meal_time → order_type：端侧提交 orderType，旧列名会导致
--      传值被静默忽略（午餐单落库成默认晚餐单）。仅旧列存在时改名。
--   2) 订单状态取值 PICKING/WAITING → ORDERING/SHOPPING，并对齐默认值。
-- =============================================================

SET @has_meal_time := (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'familymenu_order' AND COLUMN_NAME = 'meal_time'
);
SET @sql := IF(@has_meal_time > 0,
    'ALTER TABLE familymenu_order CHANGE meal_time order_type VARCHAR(16) NOT NULL DEFAULT ''DINNER'' COMMENT ''就餐时段：BREAKFAST 早餐 LUNCH 午餐 DINNER 晚餐''',
    'SELECT ''skip: meal_time 不存在或已改名'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE familymenu_order SET order_status = 'ORDERING' WHERE order_status = 'PICKING';
UPDATE familymenu_order SET order_status = 'SHOPPING' WHERE order_status = 'WAITING';

ALTER TABLE familymenu_order
    MODIFY COLUMN order_status VARCHAR(16) NOT NULL DEFAULT 'ORDERING'
    COMMENT '状态：ORDERING 点单中 SHOPPING 买菜中 COOKING 烹饪中 DONE 已上桌（与小程序端状态机一致）';
