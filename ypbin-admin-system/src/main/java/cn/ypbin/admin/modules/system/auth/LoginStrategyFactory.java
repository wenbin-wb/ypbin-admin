/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.auth;

import cn.ypbin.starter.core.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 登录策略工厂：收集所有 {@link LoginStrategy} 实现，按 authType 查找并派发。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Component
public class LoginStrategyFactory {

    private final Map<String, LoginStrategy> strategies;

    public LoginStrategyFactory(List<LoginStrategy> strategies) {
        this.strategies = strategies.stream()
            .collect(Collectors.toMap(
                s -> s.authType().toUpperCase(),
                Function.identity(),
                (existing, replacement) -> existing));
    }

    public LoginStrategy get(String authType) {
        LoginStrategy strategy = strategies.get(authType.toUpperCase());
        if (strategy == null) {
            throw new BusinessException("不支持的登录方式：" + authType);
        }
        return strategy;
    }
}
