-- ypbin-admin AI 知识库增强：文档原文落盘路径（支持失败重试重新向量化）

ALTER TABLE ai_document
    ADD COLUMN file_path VARCHAR(500) NULL COMMENT '原文件本地存储路径（供重试向量化读取）' AFTER error_msg;
