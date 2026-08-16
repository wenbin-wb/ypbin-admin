-- V8__ai_chat_engine.sql
-- AI 对话引擎核心表（M1 对话引擎 2.0）

-- 1. 对话会话表（session 管理）
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

-- 2. 对话消息表（message 存储）
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
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0 否 1 是）',
    INDEX idx_session (session_id, create_time),
    INDEX idx_tenant_user (tenant_id, user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息表';

-- 3. 对话角色表（persona/role 管理）
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

-- 4. 插入内置角色（5 个经典角色）
INSERT INTO ai_chat_role (id, tenant_id, name, description, avatar, system_prompt, category, temperature, is_builtin, sort, create_user, status, is_deleted)
VALUES
(1, 0, '通用助手', '全能 AI 助手，可回答各类问题、协助工作', NULL,
 '你是一个有帮助、无害、诚实的 AI 助手。请用简洁、专业的语言回答用户的问题。', 
 'assistant', 0.70, 1, 1, 1, 1, 0),

(2, 0, '翻译专家', '精通中英互译，支持多语言翻译', NULL,
 '你是一位专业翻译，擅长中英文互译。请保持原文的语气、风格和专业术语的准确性。翻译时：1) 准确传达原意 2) 符合目标语言习惯 3) 保留专业术语。直接输出翻译结果，无需解释。', 
 'translator', 0.30, 1, 2, 1, 1, 0),

(3, 0, '代码助手', '编程专家，提供代码编写、调试、优化建议', NULL,
 '你是一位资深软件工程师，精通多种编程语言和框架。回答编程问题时：1) 提供可运行的代码示例 2) 解释关键逻辑 3) 给出最佳实践建议 4) 标注潜在问题。代码用 Markdown 代码块格式化。', 
 'coder', 0.20, 1, 3, 1, 1, 0),

(4, 0, '数据分析师', '擅长数据解读、图表分析、业务洞察', NULL,
 '你是一位数据分析专家，擅长从数据中提取洞察。分析时：1) 识别关键指标和趋势 2) 给出数据驱动的结论 3) 提供可执行的建议 4) 用简洁的语言解释复杂概念。支持 SQL、Python、Excel 等工具。', 
 'analyst', 0.50, 1, 4, 1, 1, 0),

(5, 0, '创意写作', '文案创作、故事生成、内容润色', NULL,
 '你是一位富有创意的作家，擅长各类文体创作。写作时：1) 语言生动、富有感染力 2) 结构清晰、逻辑连贯 3) 根据用户需求调整风格（正式/轻松/幽默）4) 必要时提供多个版本供选择。', 
 'writer', 0.90, 1, 5, 1, 1, 0);

-- 5. 用户角色收藏表（star/favorite）
CREATE TABLE ai_chat_role_favorite (
    id BIGINT PRIMARY KEY COMMENT '收藏 ID',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色收藏表';
-- 追加到 V8：对话引擎菜单权限种子

-- 对话角色管理子菜单
INSERT INTO sys_menu (id, pid, name, type, platform_only, path, component, title, icon, sort, create_time, status, is_deleted)
VALUES (5060, 5000, 'AiRole', 'menu', 0, '/ai/role', '/ai/role/index', 'page.ai.role.title', 'carbon:user-role', 6, NOW(), 1, 0);

-- 对话权限按钮
INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
VALUES
(5012, 5001, 'AiChatList',   'button', 0, 'ai:chat:list',   'page.ai.chat.title', 2, NOW(), 1, 0),
(5013, 5001, 'AiChatCreate', 'button', 0, 'ai:chat:create', 'page.ai.chat.title', 3, NOW(), 1, 0),
(5014, 5001, 'AiChatDelete', 'button', 0, 'ai:chat:delete', 'page.ai.chat.title', 4, NOW(), 1, 0),
(5015, 5001, 'AiChatEdit',   'button', 0, 'ai:chat:edit',   'page.ai.chat.title', 5, NOW(), 1, 0),
(5061, 5060, 'AiRoleList',   'button', 0, 'ai:role:list',   'page.ai.role.title', 1, NOW(), 1, 0),
(5062, 5060, 'AiRoleCreate', 'button', 0, 'ai:role:create', 'page.ai.role.title', 2, NOW(), 1, 0),
(5063, 5060, 'AiRoleEdit',   'button', 0, 'ai:role:edit',   'page.ai.role.title', 3, NOW(), 1, 0),
(5064, 5060, 'AiRoleDelete', 'button', 0, 'ai:role:delete', 'page.ai.role.title', 4, NOW(), 1, 0);

-- 模板 + 超管角色授权
INSERT INTO sys_template_menu (template_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (5012,5013,5014,5015,5060,5061,5062,5063,5064) AND platform_only = 0
  AND NOT EXISTS (SELECT 1 FROM sys_template_menu t WHERE t.template_id=1 AND t.menu_id=sys_menu.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (5012,5013,5014,5015,5060,5061,5062,5063,5064)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu r WHERE r.role_id=1 AND r.menu_id=sys_menu.id);
