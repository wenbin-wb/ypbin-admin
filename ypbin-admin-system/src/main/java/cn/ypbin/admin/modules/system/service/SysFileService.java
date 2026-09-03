/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.admin.modules.system.entity.SysFile;
import cn.ypbin.admin.modules.system.model.query.FileQuery;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import cn.ypbin.starter.storage.model.FileInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysFileService extends BaseService<SysFile> {

    /**
     * 分页查询文件。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SysFile> pageFiles(FileQuery query);

    /**
     * 上传并记录文件元数据。
     *
     * @param file 文件
     * @param module 业务模块
     * @return 文件信息
     */
    FileInfo uploadFile(MultipartFile file, String module);

    /**
     * 删除物理文件和元数据。
     *
     * @param id 文件 ID
     */
    void deleteFile(Long id);
}
