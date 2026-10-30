-- =============================================================
-- 小程序端侧契约对齐（幂等）
--
-- 适用对象：已由旧版 deploy/sql/006 建表、尚未执行 2026-10-16 增量脚本的库。
-- 全新库的 V5 已包含最终结构，本文件对其无副作用。
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
