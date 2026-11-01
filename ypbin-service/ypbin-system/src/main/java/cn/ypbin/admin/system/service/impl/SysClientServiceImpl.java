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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
        checkClientIdUnique(req.getClientId(), null);
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        String secret = generateSecret();
        client.setClientSecret(secret);
        if (!save(client)) {
            throw new BusinessException("客户端创建失败");
        }
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
        // 只 set 目标列：new SysClient() + updateById 会带上 BaseEntity 的 status 默认值
        boolean updated = update(new LambdaUpdateWrapper<SysClient>()
            .eq(SysClient::getId, id)
            .set(SysClient::getClientSecret, secret));
        if (!updated) {
            throw new BusinessException("密钥重置失败");
        }
        return new ClientCredentialResp(client.getClientId(), secret);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClient(Long id, SysClientSaveReq req) {
        SysClient existing = getById(id);
        if (existing == null) {
            throw new BusinessException("客户端不存在");
        }
        checkClientIdUnique(req.getClientId(), id);
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        client.setId(id);
        client.setStatus(existing.getStatus());
        if (!updateById(client)) {
            throw new BusinessException("客户端更新失败");
        }
    }

    /**
     * 客户端 ID 唯一性校验（与库内唯一键同口径：已逻辑删除的客户端不占用 ID）。
     *
     * @param clientId  客户端 ID
     * @param excludeId 编辑时排除自身 ID，新增传 {@code null}
     */
    private void checkClientIdUnique(String clientId, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysClient>()
            .eq(SysClient::getClientId, clientId)
            .ne(excludeId != null, SysClient::getId, excludeId));
        if (exists) {
            throw new BusinessException("客户端 ID 已存在：" + clientId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClient(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("客户端不存在");
        }
        if (!removeById(id)) {
            throw new BusinessException("客户端删除失败");
        }
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
