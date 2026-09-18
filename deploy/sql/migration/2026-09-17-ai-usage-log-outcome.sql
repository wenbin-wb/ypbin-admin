-- =============================================================
-- 增量升级：ai_usage_log 支持 outcome 终局结果与 NULL 用量语义（2026-09-17）
--
-- 适用对象：**已经在跑的库**（表已存在）。全新安装不需要执行本文件——
--           deploy/sql/003-ai-schema.sql 已含最终结构。
-- 为什么单独放子目录：install.sh 用 `deploy/sql/*.sql` 通配执行初始化脚本，
--           本文件放在 migration/ 子目录内**不会**被自动执行（避免在全新库上重复 ALTER 报错）。
-- 执行方式：mysql -uroot -p ypbin_admin < 2026-09-17-ai-usage-log-outcome.sql
--
-- 兼容性：全部为 MODIFY（收窄 NOT NULL 为可空属于放宽，不会因存量数据失败）与 ADD COLUMN；
--        存量行的 outcome 由 DEFAULT 'success' 补齐（旧实现只在成功路径写用量），
--        存量 token 的 0 值语义无法区分「真实 0」与「上游未回报」，**本次不回填**（保持原值），
--        仅对新增数据按 NULL=未回报 如实落库。
-- =============================================================

-- 1) 用户维度：分享页/挂件/知识库检索问答是匿名入口，本就没有用户，NULL = 未知（不再伪造 0 或用户）
ALTER TABLE ai_usage_log
    MODIFY COLUMN user_id BIGINT NULL COMMENT '用户 ID（匿名入口无用户，NULL=未知）';

-- 2) 三个 token 列改为可空：NULL = 上游未回报用量，禁止折算成 0（0 会作为真实用量参与计费/统计）
ALTER TABLE ai_usage_log
    MODIFY COLUMN input_tokens INT NULL COMMENT '输入 Token（NULL=上游未回报）',
    MODIFY COLUMN output_tokens INT NULL COMMENT '输出 Token（NULL=上游未回报）',
    MODIFY COLUMN total_tokens INT NULL COMMENT '合计 Token（NULL=上游未回报）';

-- 3) 终局结果：区分成功/失败/取消（此前失败与取消的用量被静默丢弃，无从区分）
ALTER TABLE ai_usage_log
    ADD COLUMN outcome VARCHAR(20) NOT NULL DEFAULT 'success'
        COMMENT '终局结果：success 成功 | failure 失败 | cancelled 已取消' AFTER latency_ms;

-- 4) 失败原因摘要（成功与取消为 NULL）
ALTER TABLE ai_usage_log
    ADD COLUMN error_message VARCHAR(500) NULL
        COMMENT '失败原因摘要（成功与取消为 NULL）' AFTER outcome;
