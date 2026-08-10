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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

/**
 * 一次性平台管理员初始化执行器测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class SecurityBootstrapRunnerTest {

    @Test
    void shouldSkipWhenDisabled() {
        SecurityBootstrapProperties properties = new SecurityBootstrapProperties();
        SecurityBootstrapService service = mock(SecurityBootstrapService.class);
        SecurityBootstrapRunner runner = new SecurityBootstrapRunner(properties, service);

        runner.run(mock(ApplicationArguments.class));

        verify(service, never()).initialize(same(properties), anyString());
    }

    @Test
    void shouldInitializeOnceWhenEnabled() {
        SecurityBootstrapProperties properties = new SecurityBootstrapProperties();
        properties.setEnabled(true);
        SecurityBootstrapService service = mock(SecurityBootstrapService.class);
        when(service.initialize(same(properties), anyString())).thenReturn(true);
        SecurityBootstrapRunner runner = new SecurityBootstrapRunner(properties, service);

        runner.run(mock(ApplicationArguments.class));

        verify(service).initialize(same(properties), anyString());
    }
}
