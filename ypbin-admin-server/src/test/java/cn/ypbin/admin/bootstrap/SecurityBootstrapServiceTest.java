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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.security.password.policy.PasswordCheckResult;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 一次性平台管理员初始化服务测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class SecurityBootstrapServiceTest {

    @Test
    void shouldRejectMissingUsernameBeforeClaiming() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordValidator passwordValidator = mock(PasswordValidator.class);
        SecurityBootstrapService service =
            new SecurityBootstrapService(jdbcTemplate, passwordValidator);
        SecurityBootstrapProperties properties = validProperties();
        properties.setUsername(" ");

        assertThatThrownBy(() -> service.initialize(properties, "instance-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("平台管理员初始化账号不能为空");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void shouldRejectPasswordThatViolatesPolicy() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordValidator passwordValidator = mock(PasswordValidator.class);
        when(passwordValidator.check("weak", "platform-admin"))
            .thenReturn(PasswordCheckResult.fail("密码长度不足"));
        SecurityBootstrapService service =
            new SecurityBootstrapService(jdbcTemplate, passwordValidator);
        SecurityBootstrapProperties properties = validProperties();
        properties.setPassword("weak");

        assertThatThrownBy(() -> service.initialize(properties, "instance-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("密码长度不足");
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void shouldReturnFalseWhenAnotherInstanceClaimedBootstrap() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordValidator passwordValidator = mock(PasswordValidator.class);
        when(passwordValidator.check("Secure9876!", "platform-admin"))
            .thenReturn(PasswordCheckResult.pass());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.query(
                anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class)))
            .thenReturn(List.of("RUNNING"));
        SecurityBootstrapService service =
            new SecurityBootstrapService(jdbcTemplate, passwordValidator);

        boolean initialized = service.initialize(validProperties(), "instance-1");

        assertThat(initialized).isFalse();
        verify(jdbcTemplate, never())
            .queryForObject(
                anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>any(), any(Object[].class));
    }

    private SecurityBootstrapProperties validProperties() {
        SecurityBootstrapProperties properties = new SecurityBootstrapProperties();
        properties.setUsername("platform-admin");
        properties.setPassword("Secure9876!");
        properties.setRealName("平台管理员");
        properties.setTenantId(1L);
        return properties;
    }
}
