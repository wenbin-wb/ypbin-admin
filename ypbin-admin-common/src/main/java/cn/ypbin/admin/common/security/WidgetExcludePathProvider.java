/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.security;

import cn.ypbin.starter.security.satoken.SecurityExcludePathProvider;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * 网页挂件公开接口的登录放行贡献。
 *
 * <p>挂件接口通过知识库专属令牌自证身份，必须免于登录拦截；令牌同时携带租户归属，
 * 不绕过租户隔离。</p>
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Component
@ConditionalOnClass(SecurityExcludePathProvider.class)
public class WidgetExcludePathProvider implements SecurityExcludePathProvider {

    @Override
    public List<String> excludePaths() {
        return List.of("/widget/**");
    }
}
