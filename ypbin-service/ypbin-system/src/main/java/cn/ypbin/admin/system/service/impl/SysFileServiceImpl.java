/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.entity.SysFile;
import cn.ypbin.admin.system.mapper.SysFileMapper;
import cn.ypbin.admin.system.model.query.FileQuery;
import cn.ypbin.admin.system.service.SysFileService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.storage.core.FileStorageService;
import cn.ypbin.starter.storage.model.FileInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class SysFileServiceImpl extends BaseServiceImpl<SysFileMapper, SysFile> implements SysFileService {

    private static final Logger log = LoggerFactory.getLogger(SysFileServiceImpl.class);
    private static final Pattern SAFE_MODULE = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");
    private static final String ACTIVE = "ACTIVE";
    private static final String DELETE_FAILED = "DELETE_FAILED";
    private static final String STORAGE_DELETED = "STORAGE_DELETED";
    private static final String LOCATOR_MISSING = "LOCATOR_MISSING";

    private final FileStorageService fileStorageService;

    @Override
    public PageResult<SysFile> pageFiles(FileQuery query) {
        return page(query, new LambdaQueryWrapper<SysFile>()
            .like(StringUtils.hasText(query.getOriginalName()), SysFile::getOriginalName, query.getOriginalName())
            .orderByDesc(SysFile::getCreateTime));
    }

    @Override
    public FileInfo uploadFile(MultipartFile file, String module) {
        String normalizedModule = validateModule(module);
        FileInfo info;
        try (InputStream inputStream = file.getInputStream()) {
            info = fileStorageService.upload(inputStream, file.getOriginalFilename())
                .path(normalizedModule + "/")
                .contentType(file.getContentType())
                .size(file.getSize())
                .execute();
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败：" + e.getMessage());
        }

        try {
            SysFile entity = toEntity(info, normalizedModule);
            if (!save(entity)) {
                throw new IllegalStateException("文件元数据保存失败");
            }
            return info;
        } catch (RuntimeException e) {
            compensateUpload(info, e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long id) {
        SysFile file = getById(id);
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        if (STORAGE_DELETED.equals(file.getStorageStatus())) {
            removeMetadata(file);
            return;
        }
        if (!hasLocator(file)) {
            updateStorageStatus(file.getId(), LOCATOR_MISSING, "文件缺少完整存储定位信息");
            throw new BusinessException("文件缺少完整存储定位信息，无法删除物理文件");
        }

        try {
            fileStorageService.delete(file.getPlatform(), file.getBucket(), file.getPath());
        } catch (RuntimeException storageFailure) {
            try {
                updateStorageStatus(file.getId(), DELETE_FAILED, errorMessage(storageFailure));
            } catch (RuntimeException statusFailure) {
                statusFailure.addSuppressed(storageFailure);
                log.error("物理文件删除失败且状态记录失败，fileId={}", file.getId(), storageFailure);
                throw statusFailure;
            }
            throw storageFailure;
        }
        updateStorageStatus(file.getId(), STORAGE_DELETED, null);
        removeMetadata(file);
    }

    private String validateModule(String module) {
        if (!StringUtils.hasText(module) || !SAFE_MODULE.matcher(module.trim()).matches()) {
            throw new BusinessException("文件业务模块仅允许字母、数字、下划线和中划线，长度 1-32");
        }
        return module.trim();
    }

    private SysFile toEntity(FileInfo info, String module) {
        SysFile entity = new SysFile();
        entity.setPlatform(info.getPlatform());
        entity.setBucket(info.getBucket());
        entity.setPath(info.getPath());
        entity.setUrl(info.getUrl());
        entity.setOriginalName(info.getOriginalName());
        entity.setFileName(info.getFileName());
        entity.setSize(info.getSize());
        entity.setContentType(info.getContentType());
        entity.setExtension(info.getExtension());
        entity.setHash(info.getHash());
        entity.setUploadUserId(IdentityContext.getUserId().orElse(null));
        entity.setModule(module);
        entity.setStorageStatus(ACTIVE);
        return entity;
    }

    private boolean hasLocator(SysFile file) {
        return StringUtils.hasText(file.getPlatform())
            && StringUtils.hasText(file.getBucket())
            && StringUtils.hasText(file.getPath());
    }

    private void compensateUpload(FileInfo info, RuntimeException original) {
        try {
            fileStorageService.delete(info.getPlatform(), info.getBucket(), info.getPath());
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
            log.error("文件元数据保存失败且物理文件补偿删除失败，platform={}, bucket={}, path={}",
                info.getPlatform(), info.getBucket(), info.getPath(), cleanupFailure);
        }
    }

    private void removeMetadata(SysFile file) {
        if (!removeById(file.getId())) {
            throw new IllegalStateException("文件元数据删除失败，物理文件已删除，可重试删除操作");
        }
    }

    private void updateStorageStatus(Long id, String storageStatus, String errorMessage) {
        boolean updated = update(new LambdaUpdateWrapper<SysFile>()
            .eq(SysFile::getId, id)
            .set(SysFile::getStorageStatus, storageStatus)
            .set(SysFile::getErrorMessage, errorMessage));
        if (!updated) {
            throw new IllegalStateException("文件存储状态更新失败");
        }
    }

    private String errorMessage(RuntimeException e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }
}
