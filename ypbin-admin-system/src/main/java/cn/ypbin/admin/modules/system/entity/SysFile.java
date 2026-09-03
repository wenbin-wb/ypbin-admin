/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 文件管理。记录已上传文件元数据。全局表，不隔离租户。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
@TableName("sys_file")
public class SysFile extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 存储平台（local/aliyun 等） */
    private String platform;

    /** 存储桶 */
    private String bucket;

    /** 存储路径（相对存储桶，含文件名） */
    private String path;

    /** 文件 URL */
    private String url;

    /** 原始文件名 */
    private String originalName;

    /** 存储文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long size;

    /** MIME 类型 */
    private String contentType;

    /** 文件扩展名 */
    private String extension;

    /** 文件哈希 */
    private String hash;

    /** 上传人 */
    private Long uploadUserId;

    /** 所属业务模块（如 avatar/notice/document） */
    private String module;

    /** 存储状态：ACTIVE/DELETE_FAILED/STORAGE_DELETED/LOCATOR_MISSING */
    private String storageStatus;

    /** 最近一次存储操作错误 */
    private String errorMessage;
}
