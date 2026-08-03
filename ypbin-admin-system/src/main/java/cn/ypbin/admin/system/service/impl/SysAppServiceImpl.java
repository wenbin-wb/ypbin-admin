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

import cn.ypbin.admin.system.entity.SysApp;
import cn.ypbin.admin.system.mapper.SysAppMapper;
import cn.ypbin.admin.system.model.req.SysAppSaveReq;
import cn.ypbin.admin.system.service.SysAppService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 开放应用服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
public class SysAppServiceImpl extends BaseServiceImpl<SysAppMapper, SysApp> implements SysAppService {

    @Override
    public String createApp(SysAppSaveReq req) {
        SysApp app = new SysApp();
        BeanUtils.copyProperties(req, app);
        // 密钥由后端生成，不接收前端传入
        app.setAccessKey(generateKey());
        String secretKey = generateKey();
        app.setSecretKey(secretKey);
        save(app);
        // 明文 SecretKey 仅在创建时返回一次
        return secretKey;
    }

    @Override
    public String resetSecret(Long id) {
        SysApp app = getById(id);
        if (app == null) {
            throw new BusinessException("开放应用不存在");
        }
        String secretKey = generateKey();
        SysApp update = new SysApp();
        update.setId(id);
        update.setSecretKey(secretKey);
        updateById(update);
        return secretKey;
    }

    @Override
    public void updateApp(Long id, SysAppSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("开放应用不存在");
        }
        SysApp app = new SysApp();
        BeanUtils.copyProperties(req, app);
        app.setId(id);
        // 编辑不重新生成 AK/SK，保留原密钥
        updateById(app);
    }

    private String generateKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
