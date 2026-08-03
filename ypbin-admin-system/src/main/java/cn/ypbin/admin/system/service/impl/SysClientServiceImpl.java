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

import cn.ypbin.admin.system.entity.SysClient;
import cn.ypbin.admin.system.mapper.SysClientMapper;
import cn.ypbin.admin.system.model.req.SysClientSaveReq;
import cn.ypbin.admin.system.service.SysClientService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

/**
 * 登录客户端服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
public class SysClientServiceImpl extends BaseServiceImpl<SysClientMapper, SysClient>
    implements SysClientService {

    @Override
    public String createClient(SysClientSaveReq req) {
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        // 客户端密钥由后端生成，不接收前端传入
        String secret = generateSecret();
        client.setClientSecret(secret);
        save(client);
        // 明文密钥仅在创建时返回一次，供前端展示后自行保存
        return secret;
    }

    @Override
    public String resetSecret(Long id) {
        SysClient client = getById(id);
        if (client == null) {
            throw new BusinessException("客户端不存在");
        }
        String secret = generateSecret();
        SysClient update = new SysClient();
        update.setId(id);
        update.setClientSecret(secret);
        updateById(update);
        return secret;
    }

    private String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void updateClient(Long id, SysClientSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("客户端不存在");
        }
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        client.setId(id);
        // 编辑不重新生成 clientSecret，保留原密钥
        updateById(client);
    }
}
