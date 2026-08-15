-- ypbin-admin AI 功能表（Phase 1-2）
-- 模型配置 / 会话 / 消息 / 知识库 / 文档 / 用量日志

-- 模型配置：支持多模型动态切换，API Key 加密存储
CREATE TABLE ai_model_config
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   INT          NOT NULL COMMENT '租户 ID',
    name        VARCHAR(100) NOT NULL COMMENT '模型显示名称',
    provider    VARCHAR(50)  NOT NULL COMMENT '提供商：openai | deepseek | ollama | custom',
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
) COMMENT '模型配置';

-- 对话会话
CREATE TABLE ai_conversation
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   INT          NOT NULL COMMENT '租户 ID',
    user_id     BIGINT       NOT NULL COMMENT '创建用户',
    model_id    BIGINT       NULL COMMENT '使用的模型配置 ID',
    title       VARCHAR(200) NULL COMMENT '会话标题（首条消息自动截取）',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) COMMENT '对话会话';

-- 消息记录（展示用，AI Memory 独立持久化）
CREATE TABLE ai_message
(
    id              BIGINT   NOT NULL COMMENT '主键',
    tenant_id       INT      NOT NULL COMMENT '租户 ID',
    conversation_id BIGINT   NOT NULL COMMENT '会话 ID',
    role            VARCHAR(20)  NULL COMMENT '角色：user | assistant',
    content         TEXT     NULL COMMENT '消息内容（Markdown）',
    tokens          INT      NOT NULL DEFAULT 0 COMMENT 'Token 消耗',
    create_user     BIGINT   NULL COMMENT '创建人',
    create_time     DATETIME NULL COMMENT '创建时间',
    update_user     BIGINT   NULL COMMENT '更新人',
    update_time     DATETIME NULL COMMENT '更新时间',
    status          TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted      TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) COMMENT '消息记录';

-- 知识库
CREATE TABLE ai_knowledge_base
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   INT          NOT NULL COMMENT '租户 ID',
    name        VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description VARCHAR(500) NULL COMMENT '描述',
    doc_count   INT          NOT NULL DEFAULT 0 COMMENT '文档数量',
    remark      VARCHAR(500) NULL COMMENT '备注',
    create_user BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_user BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) COMMENT '知识库';

-- 文档
CREATE TABLE ai_document
(
    id                BIGINT       NOT NULL COMMENT '主键',
    tenant_id         INT          NOT NULL COMMENT '租户 ID',
    knowledge_base_id BIGINT       NOT NULL COMMENT '所属知识库',
    filename          VARCHAR(255) NULL COMMENT '文件名',
    file_size         BIGINT       NULL COMMENT '文件大小（字节）',
    chunk_count       INT          NOT NULL DEFAULT 0 COMMENT '切片数量',
    status            TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 处理中 1 就绪 2 失败',
    error_msg         VARCHAR(500) NULL COMMENT '失败原因',
    create_user       BIGINT       NULL COMMENT '上传人',
    create_time       DATETIME     NULL COMMENT '上传时间',
    update_user       BIGINT       NULL COMMENT '更新人',
    update_time       DATETIME     NULL COMMENT '更新时间',
    is_deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) COMMENT '知识库文档';

-- Prompt 模板
CREATE TABLE ai_prompt_template
(
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   INT          NOT NULL COMMENT '租户 ID',
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
) COMMENT 'Prompt 模板';

-- Token 用量日志
CREATE TABLE ai_usage_log
(
    id              BIGINT   NOT NULL COMMENT '主键',
    tenant_id       INT      NOT NULL COMMENT '租户 ID',
    user_id         BIGINT   NOT NULL COMMENT '用户',
    conversation_id BIGINT   NULL COMMENT '会话 ID',
    model_id        BIGINT   NULL COMMENT '模型配置 ID',
    model_name      VARCHAR(100) NULL COMMENT '模型名称（冗余，防改名影响统计）',
    input_tokens    INT      NOT NULL DEFAULT 0 COMMENT '输入 Token',
    output_tokens   INT      NOT NULL DEFAULT 0 COMMENT '输出 Token',
    total_tokens    INT      NOT NULL DEFAULT 0 COMMENT '合计 Token',
    latency_ms      BIGINT   NOT NULL DEFAULT 0 COMMENT '响应耗时（ms）',
    create_user     BIGINT   NULL COMMENT '创建人',
    create_time     DATETIME NULL COMMENT '创建时间',
    update_user     BIGINT   NULL COMMENT '更新人',
    update_time     DATETIME NULL COMMENT '更新时间',
    status          TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1 正常 0 停用',
    is_deleted      TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id)
) COMMENT 'Token 用量日志';

-- 菜单（追加到 V2 种子数据格式，使用 V3 统一管理）
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (5000, 0, 'AiManage', 'catalog', 0, '/ai', 'BasicLayout', 'page.ai.title', 'carbon:machine-learning', 9, NOW(), 1, 0);

INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES
(5001, 5000, 'AiChat',      'menu', 0, '/ai/chat',      '/ai/chat/index',      'page.ai.chat.title',      'carbon:chat-bot',          1, NOW(), 1, 0),
(5002, 5000, 'AiKnowledge', 'menu', 0, '/ai/knowledge', '/ai/knowledge/index', 'page.ai.knowledge.title', 'carbon:data-base',          2, NOW(), 1, 0),
(5003, 5000, 'AiConfig',    'menu', 1, '/ai/config',    '/ai/config/index',    'page.ai.config.title',    'carbon:settings-services',  3, NOW(), 1, 0);

-- 权限按钮
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES
(5011, 5001, 'AiChatSend',         'button', 0, 'ai:chat:send',          'page.ai.chat.send',          1, NOW(), 1, 0),
(5021, 5002, 'AiKnowledgeList',    'button', 0, 'ai:knowledge:list',     'page.ai.knowledge.list',     1, NOW(), 1, 0),
(5022, 5002, 'AiKnowledgeCreate',  'button', 0, 'ai:knowledge:create',   'page.ai.knowledge.create',   2, NOW(), 1, 0),
(5023, 5002, 'AiKnowledgeDelete',  'button', 0, 'ai:knowledge:delete',   'page.ai.knowledge.delete',   3, NOW(), 1, 0),
(5024, 5002, 'AiDocumentUpload',   'button', 0, 'ai:document:upload',    'page.ai.document.upload',    4, NOW(), 1, 0),
(5025, 5002, 'AiDocumentDelete',   'button', 0, 'ai:document:delete',    'page.ai.document.delete',    5, NOW(), 1, 0),
(5031, 5003, 'AiModelList',        'button', 1, 'ai:model:list',         'page.ai.model.list',         1, NOW(), 1, 0),
(5032, 5003, 'AiModelCreate',      'button', 1, 'ai:model:create',       'page.ai.model.create',       2, NOW(), 1, 0),
(5033, 5003, 'AiModelEdit',        'button', 1, 'ai:model:edit',         'page.ai.model.edit',         3, NOW(), 1, 0),
(5034, 5003, 'AiModelDelete',      'button', 1, 'ai:model:delete',       'page.ai.model.delete',       4, NOW(), 1, 0);

-- Prompt 模板和用量统计菜单（5040-5050 段）
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES
(5040, 5000, 'AiPrompt',  'menu', 0, '/ai/prompt',  '/ai/prompt/index',  'page.ai.prompt.title',  'carbon:template',       4, NOW(), 1, 0),
(5050, 5000, 'AiUsage',   'menu', 1, '/ai/usage',   '/ai/usage/index',   'page.ai.usage.title',   'carbon:analytics',      5, NOW(), 1, 0);

-- 全部权限模板补充 AI 菜单与按钮权限（追加 menu_id 到 sys_template_menu）
INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 5000 AND 5099 AND type IN ('catalog', 'menu', 'button') AND platform_only = 0;

-- 平台超级管理员获得 AI 平台专用菜单（模型配置、用量统计）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 5000 AND 5099 AND platform_only = 1;