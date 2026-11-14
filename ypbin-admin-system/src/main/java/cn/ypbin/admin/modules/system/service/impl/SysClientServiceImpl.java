/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import cn.ypbin.admin.modules.system.entity.SysClient;
import cn.ypbin.admin.modules.system.mapper.SysClientMapper;
import cn.ypbin.admin.modules.system.model.req.SysClientSaveReq;
import cn.ypbin.admin.modules.system.model.resp.ClientCredentialResp;
import cn.ypbin.admin.modules.system.model.resp.ClientResp;
import cn.ypbin.admin.modules.system.service.SysClientService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 登录客户端服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysClientServiceImpl extends BaseServiceImpl<SysClientMapper, SysClient>
    implements SysClientService {

    /** 默认客户端配置：未声明客户端身份的请求会回落到它 */
    private final SecurityProperties securityProperties;

    @Override
    public List<ClientResp> listClients() {
        return list().stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientResp createClient(SysClientSaveReq req) {
        checkClientIdUnique(req.getClientId(), null);
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        // 默认不生成密钥：无密钥即可登录；需要密钥的端由「重置密钥」显式开启
        save(client);
        return toResp(client);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClientCredentialResp resetSecret(Long id) {
        SysClient client = getById(id);
        if (client == null) {
            throw new BusinessException("客户端不存在");
        }
        rejectDefaultClient(client, "配置密钥");
        String secret = generateSecret();
        // 只 set 目标列：new SysClient() + updateById 会带上 BaseEntity 的 status 默认值
        update(new LambdaUpdateWrapper<SysClient>()
            .eq(SysClient::getId, id)
            .set(SysClient::getClientSecret, secret));
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
        if (isDefaultClient(existing) && !req.getClientId().equals(existing.getClientId())) {
            throw new BusinessException("默认客户端不允许修改客户端 ID");
        }
        SysClient client = new SysClient();
        BeanUtils.copyProperties(req, client);
        client.setId(id);
        client.setStatus(existing.getStatus());
        updateById(client);
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
    public void clearSecret(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("客户端不存在");
        }
        // 只 set 目标列：new SysClient() + updateById 会带上 BaseEntity 的 status 默认值
        update(new LambdaUpdateWrapper<SysClient>()
            .eq(SysClient::getId, id)
            .set(SysClient::getClientSecret, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysClient client = getById(id);
        if (client == null) {
            throw new BusinessException("客户端不存在");
        }
        if (EntityStatus.DISABLED.getCode().equals(status)) {
            rejectDefaultClient(client, "停用");
        }
        update(new LambdaUpdateWrapper<SysClient>()
            .eq(SysClient::getId, id)
            .set(SysClient::getStatus, status));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClient(Long id) {
        SysClient client = getById(id);
        if (client == null) {
            throw new BusinessException("客户端不存在");
        }
        rejectDefaultClient(client, "删除");
        removeById(id);
    }

    /**
     * 是否为配置的默认客户端。它承接所有未声明身份的登录，改动或停用会导致全站无法登录。
     */
    private boolean isDefaultClient(SysClient client) {
        return client.getClientId() != null
            && client.getClientId().equals(securityProperties.getDefaultClientId());
    }

    private void rejectDefaultClient(SysClient client, String action) {
        if (isDefaultClient(client)) {
            throw new BusinessException("默认客户端不允许" + action);
        }
    }

    private ClientResp toResp(SysClient client) {
        ClientResp resp = new ClientResp();
        BeanUtils.copyProperties(client, resp);
        resp.setSecretConfigured(StringUtils.hasText(client.getClientSecret()));
        resp.setDefaultClient(isDefaultClient(client));
        return resp;
    }

    private String generateSecret() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
