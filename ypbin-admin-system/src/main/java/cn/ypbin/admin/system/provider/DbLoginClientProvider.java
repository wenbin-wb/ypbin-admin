/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import cn.ypbin.admin.system.entity.SysClient;
import cn.ypbin.admin.system.mapper.SysClientMapper;
import cn.ypbin.starter.security.client.LoginClient;
import cn.ypbin.starter.security.client.LoginClientProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 登录客户端来源：从数据库读取客户端配置，支持后台可视化管理。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Component
@RequiredArgsConstructor
public class DbLoginClientProvider implements LoginClientProvider {

    private final SysClientMapper clientMapper;

    @Override
    public Optional<LoginClient> findByClientId(String clientId) {
        SysClient entity = clientMapper.selectOne(
            new LambdaQueryWrapper<SysClient>().eq(SysClient::getClientId, clientId));
        if (entity == null) {
            return Optional.empty();
        }
        LoginClient client = new LoginClient();
        client.setClientId(entity.getClientId());
        client.setClientSecret(entity.getClientSecret());
        client.setClientType(entity.getClientType());
        client.setAuthTypes(parseAuthTypes(entity.getAuthTypes()));
        client.setTimeout(entity.getTimeout());
        client.setActiveTimeout(entity.getActiveTimeout());
        client.setConcurrent(entity.getConcurrentEnabled() != null
            ? entity.getConcurrentEnabled() == 1 : null);
        client.setMaxLoginCount(entity.getMaxLoginCount());
        return Optional.of(client);
    }

    private Set<String> parseAuthTypes(String authTypes) {
        if (authTypes == null || authTypes.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(authTypes.split(","))
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
