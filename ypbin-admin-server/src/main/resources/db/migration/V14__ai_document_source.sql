-- V14: ai_document 增加来源类型和来源 URL 字段
-- source_type: UPLOAD(文件上传)/URL(单页)/SITEMAP(Sitemap批量)/RSS(RSS订阅)
ALTER TABLE ai_document
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型 UPLOAD/URL/SITEMAP/RSS',
    ADD COLUMN source_url  VARCHAR(1024) DEFAULT NULL COMMENT '来源 URL（URL/SITEMAP/RSS 导入时使用）';
