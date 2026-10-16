-- =============================================================
-- 增量升级：小程序端侧契约对齐（2026-10-16）
--
-- 适用对象：**已经在跑的库**（表已存在）。全新安装不需要执行本文件——
--           deploy/sql/006-miniapp-schema.sql 已含最终结构。
-- 为什么单独放子目录：install.sh 用 `deploy/sql/*.sql` 通配执行初始化脚本，
--           本文件放在 migration/ 子目录内**不会**被自动执行。
-- 执行方式：mysql -uroot -p ypbin_admin < 2026-10-16-miniapp-contract-align.sql
--
-- 内容：
--   1) 新增 cardtab_game_template（玩法模板）与 cardtab_feedback（意见反馈）两张表；
--   2) familymenu_order.meal_time → order_type：端侧提交的是 orderType，旧列名导致传值被
--      静默忽略（午餐单落库成默认晚餐单）；铁律「字段全程同名」，列名须与契约一致；
--   3) familymenu_order.order_status 取值 PICKING/WAITING → ORDERING/SHOPPING：
--      端侧按 ORDERING/SHOPPING/COOKING/DONE 渲染四步进度条，旧值会被当作未知状态。
--
-- 兼容性：建表用 IF NOT EXISTS、改名用 information_schema 存在性判断，均可重复执行；
--         状态 UPDATE 为幂等（改过之后再跑不匹配任何行）。
-- ⚠️ 状态取值迁移不可逆方向明确：旧值 PICKING/WAITING 迁移后不再出现，
--    若回滚代码需一并把这两类值改回（历史数据无标记，无法自动区分）。
-- =============================================================

-- -------------------------------------------------------------
-- 1. 新增：玩法模板表（端侧 /cardtab/game-templates）
-- -------------------------------------------------------------
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

-- -------------------------------------------------------------
-- 2. 新增：意见反馈表（端侧 /cardtab/feedback）
-- -------------------------------------------------------------
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
-- 3. 字段改名：familymenu_order.meal_time → order_type
--    原因：端侧提交的是 orderType，旧列名 mealTime 导致端侧传值被静默忽略
--          （午餐单被记成默认的晚餐单）。字段全程同名（列=实体=DTO=接口）。
--    幂等：仅在旧列存在时改名。
-- -------------------------------------------------------------
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

-- -------------------------------------------------------------
-- 4. 订单状态取值对齐端侧状态机
--    PICKING  → ORDERING（点单中）
--    WAITING  → SHOPPING（买菜中）
--    COOKING / DONE 不变
--    原因：端侧按 ORDERING/SHOPPING/COOKING/DONE 渲染四步进度条，
--          旧值会被当作未知状态落到兜底分支。
-- -------------------------------------------------------------
UPDATE familymenu_order SET order_status = 'ORDERING' WHERE order_status = 'PICKING';
UPDATE familymenu_order SET order_status = 'SHOPPING' WHERE order_status = 'WAITING';

-- 列默认值同步为新起点值
ALTER TABLE familymenu_order
    MODIFY COLUMN order_status VARCHAR(16) NOT NULL DEFAULT 'ORDERING'
    COMMENT '状态：ORDERING 点单中 SHOPPING 买菜中 COOKING 烹饪中 DONE 已上桌（与小程序端状态机一致）';
