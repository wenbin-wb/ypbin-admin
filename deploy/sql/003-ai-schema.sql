-- =============================================================
-- ypbin-admin AI 功能表（最终结构，唯一结构来源）
-- 覆盖：模型配置 / 知识库 / 文档 / Prompt 模板 / 用量日志 / 对话引擎（会话/消息/角色/收藏）
--       / 检索问答日志 / 文档分块 / Spring AI JDBC 会话记忆
-- 约定：全部 tenant_id 为 BIGINT（对齐 TenantBaseEntity 的 Long tenantId）
--       对话引擎表（ai_chat_*）承载 M1 对话引擎 2.0；旧版 ai_conversation/ai_message 已废弃移除
-- =============================================================

-- 模型配置：支持多模型动态切换，API Key 加密存储
CREATE TABLE ai_model_config
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户 ID',
    name        VARCHAR(100) NOT NULL COMMENT '模型显示名称',
    provider    VARCHAR(50)  NOT NULL COMMENT '提供商：openai | deepseek | ollama | custom',
    model_type  VARCHAR(20)  NOT NULL DEFAULT 'CHAT' COMMENT '模型类型：CHAT 对话 | EMBEDDING 向量化',
    api_key     VARCHAR(500) NULL COMMENT 'API Key（AES-GCM 加密存储）',
    base_url    VARCHAR(300) NULL COMMENT '接口基础地址（Ollama/自定义必填）',
    model_name  VARCHAR(100) NOT NULL COMMENT '模型名称，如 deepseek-v4-flash、gpt-5.6',
    is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认模型',
    remark      VARCHAR(500) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '模型配置';

-- 知识库
CREATE TABLE ai_knowledge_base
(
    id                BIGINT       NOT NULL COMMENT '主键',
    tenant_id         BIGINT       NOT NULL COMMENT '租户 ID',
    name              VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description       VARCHAR(500) NULL COMMENT '描述',
    doc_count         INT          NOT NULL DEFAULT 0 COMMENT '文档数量',
    icon              VARCHAR(64)  NULL DEFAULT NULL COMMENT '知识库图标（图标名，前端卡片展示）',
    remark            VARCHAR(500) NULL COMMENT '备注',
    widget_token      VARCHAR(64)  NULL COMMENT '网页挂件令牌（非空=启用公开问答）',
    widget_enabled    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '挂件是否启用 0/1',
    share_token       VARCHAR(64)  NULL COMMENT '分享令牌（非空=启用公开分享）',
    share_enabled     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '分享是否启用 0/1',
    share_expire_time DATETIME     NULL COMMENT '分享过期时间（NULL=永不过期）',
    share_password    VARCHAR(128) NULL COMMENT '访问密码 SHA-256 哈希（NULL=无需密码）',
    create_user       BIGINT       NULL COMMENT '创建人',
    create_time       DATETIME     NULL COMMENT '创建时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_kb_widget_token (widget_token),
    UNIQUE KEY uk_ai_kb_share_token (share_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识库';

-- 文档（支持 URL/Sitemap/RSS 导入，原文落盘供重试向量化）
CREATE TABLE ai_document
(
    id                BIGINT       NOT NULL COMMENT '主键',
    tenant_id         BIGINT       NOT NULL COMMENT '租户 ID',
    knowledge_base_id BIGINT       NOT NULL COMMENT '所属知识库',
    filename          VARCHAR(255) NULL COMMENT '文件名',
    file_size         BIGINT       NULL COMMENT '文件大小（字节）',
    chunk_count       INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 处理中 1 就绪 2 失败',
    error_msg         VARCHAR(500) NULL COMMENT '失败原因',
    file_path         VARCHAR(500) NULL COMMENT '原文件本地存储路径（供重试向量化读取）',
    source_type       VARCHAR(20)  NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型 UPLOAD/URL/SITEMAP/RSS',
    source_url        VARCHAR(1024) DEFAULT NULL COMMENT '来源 URL（URL/SITEMAP/RSS 导入时使用）',
    create_user       BIGINT       NULL COMMENT '上传人',
    create_time       DATETIME     NULL COMMENT '上传时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ai_document_kb (knowledge_base_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '知识库文档';

-- 文档分块（向量化时落库，用于分块可视化与诊断）
CREATE TABLE ai_document_chunk
(
    id                BIGINT   NOT NULL COMMENT '主键',
    tenant_id         BIGINT   NOT NULL COMMENT '租户 ID',
    knowledge_base_id BIGINT   NOT NULL COMMENT '所属知识库',
    document_id       BIGINT   NOT NULL COMMENT '所属文档',
    chunk_index       INT      NOT NULL COMMENT '分块序号（0 起）',
    content           TEXT     NOT NULL COMMENT '分块内容',
    char_count        INT      NOT NULL DEFAULT 0 COMMENT '字符数',
    create_user       BIGINT   NULL COMMENT '创建人',
    create_time       DATETIME NULL COMMENT '创建时间',
    update_user       BIGINT   NULL COMMENT '更新人',
    update_time       DATETIME NULL COMMENT '更新时间',
    status            TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted        TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ai_chunk_doc (document_id, chunk_index),
    KEY idx_ai_chunk_kb (knowledge_base_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 文档分块';

-- Prompt 模板
CREATE TABLE ai_prompt_template
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户 ID',
    name        VARCHAR(100) NOT NULL COMMENT '模板名称',
    category    VARCHAR(50)  NULL COMMENT '分类，如 coding、writing、analysis',
    template    TEXT         NOT NULL COMMENT '提示词模板，支持 {username} 占位符',
    description VARCHAR(300) NULL COMMENT '描述',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Prompt 模板';

-- Token 用量日志
CREATE TABLE ai_usage_log
(
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    user_id         BIGINT       NOT NULL COMMENT '用户',
    conversation_id BIGINT       NULL COMMENT '会话 ID',
    model_id        BIGINT       NULL COMMENT '模型配置 ID',
    model_name      VARCHAR(100) NULL COMMENT '模型名称（冗余，防改名影响统计）',
    input_tokens    INT          NOT NULL DEFAULT 0 COMMENT '输入 Token',
    output_tokens   INT          NOT NULL DEFAULT 0 COMMENT '输出 Token',
    total_tokens    INT          NOT NULL DEFAULT 0 COMMENT '合计 Token',
    latency_ms      BIGINT       NOT NULL DEFAULT 0 COMMENT '响应耗时（ms）',
    create_user     BIGINT       NULL COMMENT '创建人',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_user     BIGINT       NULL COMMENT '更新人',
    update_time     DATETIME     NULL COMMENT '更新时间',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ai_usage_tenant_time (tenant_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'Token 用量日志';

-- 检索问答日志（统计搜索热词与问答趋势）
CREATE TABLE ai_query_log
(
    id                BIGINT       NOT NULL COMMENT '主键',
    tenant_id         BIGINT       NOT NULL COMMENT '租户 ID',
    knowledge_base_id BIGINT       NOT NULL COMMENT '知识库 ID',
    query             VARCHAR(500) NOT NULL COMMENT '检索/问答问题',
    source            VARCHAR(20)  NOT NULL DEFAULT 'QUERY' COMMENT '来源：QUERY 问答 / SEARCH 检索测试 / RERANK 重排测试 / MULTIPLE 多库测试',
    create_user       BIGINT       NULL COMMENT '创建人',
    create_time       DATETIME     NULL COMMENT '创建时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_ai_query_log_tenant_time (tenant_id, create_time),
    KEY idx_ai_query_log_kb (knowledge_base_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'AI 检索问答日志';

-- =============================================================
-- AI 对话引擎（M1 对话引擎 2.0）
-- =============================================================

-- 对话会话表（session 管理）
CREATE TABLE ai_chat_session (
    id BIGINT PRIMARY KEY COMMENT '会话 ID',
    tenant_id BIGINT NOT NULL COMMENT '租户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    title VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题（自动生成或用户修改）',
    role_id BIGINT COMMENT '绑定角色 ID（NULL 为默认助手）',
    model_id BIGINT COMMENT '使用的模型配置 ID',
    context_window INT NOT NULL DEFAULT 10 COMMENT '上下文窗口大小（保留最近 N 轮对话）',
    total_tokens INT NOT NULL DEFAULT 0 COMMENT '累计消耗 token 数',
    message_count INT NOT NULL DEFAULT 0 COMMENT '消息总数',
    is_pinned TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶（0 否 1 是）',
    last_message_at DATETIME COMMENT '最后一条消息时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '更新人',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0 归档 1 活跃）',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 否 1 是）',
    INDEX idx_tenant_user (tenant_id, user_id, last_message_at),
    INDEX idx_user_pinned (user_id, is_pinned, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话表';

-- 对话消息表（message 存储，含审计公共列）
CREATE TABLE ai_chat_message (
    id BIGINT PRIMARY KEY COMMENT '消息 ID',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    tenant_id BIGINT NOT NULL COMMENT '租户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    parent_id BIGINT COMMENT '父消息 ID（支持分支对话，暂不实现）',
    role VARCHAR(20) NOT NULL COMMENT '角色（user/assistant/system/tool）',
    content TEXT NOT NULL COMMENT '消息内容',
    tokens INT COMMENT 'token 消耗（assistant 消息记录）',
    model_name VARCHAR(100) COMMENT '使用的模型名称（assistant 消息记录）',
    finish_reason VARCHAR(20) COMMENT '结束原因（stop/length/tool_calls）',
    tool_calls JSON COMMENT '工具调用记录（JSON 数组）',
    images JSON COMMENT '图片附件（多模态输入，JSON 数组：[{url, thumbnail}]）',
    metadata JSON COMMENT '扩展元数据（latency_ms、temperature 等）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_user BIGINT NULL COMMENT '创建人',
    update_user BIGINT NULL COMMENT '更新人',
    update_time DATETIME NULL COMMENT '更新时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 否 1 是）',
    INDEX idx_session (session_id, create_time),
    INDEX idx_tenant_user (tenant_id, user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息表';

-- 对话角色表（persona/role 管理）
CREATE TABLE ai_chat_role (
    id BIGINT PRIMARY KEY COMMENT '角色 ID',
    tenant_id BIGINT NOT NULL COMMENT '租户 ID（0 为内置全局角色）',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) COMMENT '角色描述',
    avatar VARCHAR(500) COMMENT '角色头像 URL',
    system_prompt TEXT NOT NULL COMMENT '系统提示词（role 的 prompt 模板）',
    category VARCHAR(50) COMMENT '分类（assistant/translator/coder/analyst）',
    model_preference VARCHAR(100) COMMENT '推荐模型（model_name，可选）',
    temperature DECIMAL(3,2) DEFAULT 0.70 COMMENT '默认温度参数',
    is_builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置（0 否 1 是）',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人',
    update_user BIGINT COMMENT '更新人',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0 停用 1 启用）',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 否 1 是）',
    INDEX idx_tenant_category (tenant_id, category, sort),
    INDEX idx_builtin (is_builtin, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话角色表';

-- 用户角色收藏表（star/favorite，含审计公共列）
CREATE TABLE ai_chat_role_favorite (
    id BIGINT PRIMARY KEY COMMENT '收藏 ID',
    tenant_id BIGINT NULL COMMENT '租户 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    create_user BIGINT NULL COMMENT '创建人',
    update_user BIGINT NULL COMMENT '更新人',
    update_time DATETIME NULL COMMENT '更新时间',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色收藏表';

-- Spring AI JdbcChatMemoryRepository 持久化会话记忆表（ypbin.ai.memory.type=jdbc 时使用）
-- 表名与列名须与 Spring AI schema-mysql.sql 完全一致（查询语句硬编码大写表名），勿重命名
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    `conversation_id` VARCHAR(36) NOT NULL,
    `content` TEXT NOT NULL,
    `type` ENUM('USER', 'ASSISTANT', 'SYSTEM', 'TOOL') NOT NULL,
    `timestamp` TIMESTAMP NOT NULL,
    `sequence_id` BIGINT NOT NULL,
    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX` (`conversation_id`, `timestamp`),
    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX` (`conversation_id`, `sequence_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 会话记忆（Spring AI JDBC 持久化）';
