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
import cn.ypbin.admin.system.model.resp.ClientCredentialResp;
import cn.ypbin.admin.system.model.resp.ClientResp;
import cn.ypbin.admin.system.service.SysClientService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<ClientResp> listClients() {
        return list().stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientCredentialResp createClient(SysClientSaveReq req) {
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        String secret = generateSecret();
        client.setClientSecret(secret);
        save(client);
        return new ClientCredentialResp(client.getClientId(), secret);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientCredentialResp resetSecret(Long id) {
        SysClient client = getById(id);
        if (client == null) {
            throw new BusinessException("客户端不存在");
        }
        String secret = generateSecret();
        SysClient update = new SysClient();
        update.setId(id);
        update.setClientSecret(secret);
        updateById(update);
        return new ClientCredentialResp(client.getClientId(), secret);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClient(Long id, SysClientSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("客户端不存在");
        }
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        client.setId(id);
        updateById(client);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClient(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("客户端不存在");
        }
        removeById(id);
    }

    private ClientResp toResp(SysClient client) {
        ClientResp resp = new ClientResp();
        BeanUtils.copyProperties(client, resp);
        return resp;
    }

    private String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
