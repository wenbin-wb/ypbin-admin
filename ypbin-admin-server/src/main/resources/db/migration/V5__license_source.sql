-- =============================================================
-- ypbin-admin 商业授权模块：预留签发来源字段（阶段三：联机校验 + 支付预留）
-- source 用于记录授权从何而来：manual 手工签发（默认）/ payment 支付自动获取（预留，暂未实现）。
-- 为将来「支付回调自动签发」做字段级预留，当前所有记录均为 manual。
-- =============================================================

ALTER TABLE sys_license
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'manual'
        COMMENT '签发来源：manual 手工 / payment 支付（预留）' AFTER delivery_mode;
