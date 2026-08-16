-- Spring AI JdbcChatMemoryRepository 持久化会话记忆表（ypbin.ai.memory.type=jdbc 时使用）
-- 表名与列名须与 Spring AI schema-mysql.sql 完全一致（查询语句硬编码大写表名）
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    `conversation_id` VARCHAR(36) NOT NULL,
    `content` TEXT NOT NULL,
    `type` ENUM('USER', 'ASSISTANT', 'SYSTEM', 'TOOL') NOT NULL,
    `timestamp` TIMESTAMP NOT NULL,
    `sequence_id` BIGINT NOT NULL,
    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX` (`conversation_id`, `timestamp`),
    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX` (`conversation_id`, `sequence_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 会话记忆（Spring AI JDBC 持久化）';
