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

import cn.ypbin.admin.system.entity.SysApp;
import cn.ypbin.admin.system.mapper.SysAppMapper;
import cn.ypbin.starter.sign.core.SignApp;
import cn.ypbin.starter.sign.core.SignAppProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 开放应用来源：从数据库读取应用凭证。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
@RequiredArgsConstructor
public class DbSignAppProvider implements SignAppProvider {

    private final SysAppMapper appMapper;

    @Override
    public Optional<SignApp> findByAccessKey(String accessKey) {
        SysApp entity = appMapper.selectOne(
            new LambdaQueryWrapper<SysApp>().eq(SysApp::getAccessKey, accessKey));
        if (entity == null) {
            return Optional.empty();
        }
        SignApp app = new SignApp();
        app.setAccessKey(entity.getAccessKey());
        app.setSecretKey(entity.getSecretKey());
        app.setAppName(entity.getAppName());
        app.setExpireTime(entity.getExpireTime());
        app.setEnabled(entity.getEnabled() != null && entity.getEnabled() == 1);
        return Optional.of(app);
    }
}
