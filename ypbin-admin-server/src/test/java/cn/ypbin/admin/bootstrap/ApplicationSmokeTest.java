/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.web.handler.GlobalExceptionHandler;
import cn.ypbin.starter.web.util.WebRequestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 应用启动冒烟测试：核心基础设施类可装配、响应契约稳定。
 *
 * <p>不启动完整 Spring Boot 容器（避免外部 MySQL 依赖），仅验证 admin 依赖的
 * starter 核心组件类可加载、统一响应契约符合对外约定。</p>
 *
 * @author wenbin
 * @since 2026-08-31
 */
class ApplicationSmokeTest {

    @Test
    void coreClassesShouldLoad() {
        assertThat(GlobalExceptionHandler.class).isNotNull();
        assertThat(WebRequestUtils.class).isNotNull();
        assertThat(R.class).isNotNull();
    }

    @Test
    void responseContractShouldMatch() {
        R<Void> ok = R.ok();
        assertThat(ok.getCode()).isEqualTo(200);
        assertThat(ok.isSuccess()).isTrue();

        R<Void> fail = R.fail(GlobalErrorCode.BUSINESS_ERROR);
        assertThat(fail.isSuccess()).isFalse();
        assertThat(fail.getCode()).isEqualTo(GlobalErrorCode.BUSINESS_ERROR.getCode());
    }

    @Test
    void annotationContextShouldStart() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.refresh();
            assertThat(ctx.isActive()).isTrue();
        }
    }
}
