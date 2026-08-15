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

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.starter.data.util.IdGenerator;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.security.password.policy.PasswordCheckResult;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 一次性平台管理员初始化服务。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Service
@RequiredArgsConstructor
public class SecurityBootstrapService {

    private static final String BOOTSTRAP_KEY = "platform-admin";
    private static final int ENABLED = 1;
    private static final int NOT_DELETED = 0;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordValidator passwordValidator;

    @Transactional(rollbackFor = Exception.class)
    public boolean initialize(SecurityBootstrapProperties properties, String owner) {
        BootstrapInput input = validate(properties, owner);
        int claimed = jdbcTemplate.update(
            "UPDATE sys_bootstrap_state SET state = 'RUNNING', owner = ?, update_time = NOW() "
                + "WHERE bootstrap_key = ? AND state = 'PENDING'",
            input.owner(), BOOTSTRAP_KEY);
        if (claimed == 0) {
            requireClaimedOrCompletedState();
            return false;
        }

        requireTenant(input.tenantId());
        long roleId = requirePlatformRole(input.tenantId());
        String encodedPassword = PasswordEncoderUtil.encode(input.password());
        ExistingUser existingUser = findUser(input.username());
        long userId = existingUser == null
            ? createUser(input, encodedPassword)
            : enableUser(existingUser, input, encodedPassword);
        bindRole(userId, roleId);

        int completed = jdbcTemplate.update(
            "UPDATE sys_bootstrap_state SET state = 'COMPLETED', completed_at = NOW(), update_time = NOW() "
                + "WHERE bootstrap_key = ? AND state = 'RUNNING' AND owner = ?",
            BOOTSTRAP_KEY, input.owner());
        if (completed != 1) {
            throw new IllegalStateException("平台管理员初始化状态更新失败");
        }
        return true;
    }

    private void requireClaimedOrCompletedState() {
        List<String> states = jdbcTemplate.query(
            "SELECT state FROM sys_bootstrap_state WHERE bootstrap_key = ?",
            (resultSet, rowNum) -> resultSet.getString("state"),
            BOOTSTRAP_KEY);
        if (states.size() != 1) {
            throw new IllegalStateException("平台管理员初始化状态记录不存在或不唯一");
        }
        String state = states.getFirst();
        if (!"RUNNING".equals(state) && !"COMPLETED".equals(state)) {
            throw new IllegalStateException("平台管理员初始化状态异常：" + state);
        }
    }

    private BootstrapInput validate(SecurityBootstrapProperties properties, String owner) {
        if (properties == null) {
            throw new IllegalArgumentException("平台管理员初始化配置不能为空");
        }
        String username = requireText(properties.getUsername(), "平台管理员初始化账号不能为空");
        String password = requirePassword(properties.getPassword());
        String realName = requireText(properties.getRealName(), "平台管理员姓名不能为空");
        String normalizedOwner = requireText(owner, "平台管理员初始化实例标识不能为空");
        Long tenantId = properties.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("平台管理员初始化租户 ID 必须大于 0");
        }
        PasswordCheckResult result = passwordValidator.check(password, username);
        if (!result.passed()) {
            throw new IllegalArgumentException("平台管理员初始化密码不符合安全策略：" + result.message());
        }
        return new BootstrapInput(username, password, realName, tenantId, normalizedOwner);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String requirePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalArgumentException("平台管理员初始化密码不能为空");
        }
        return password;
    }

    private void requireTenant(Long tenantId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_tenant WHERE id = ? AND status = ? AND is_deleted = ?",
            Integer.class, tenantId, ENABLED, NOT_DELETED);
        if (count == null || count != 1) {
            throw new IllegalStateException("平台管理员初始化租户不存在或已禁用：" + tenantId);
        }
    }

    private long requirePlatformRole(Long tenantId) {
        List<Long> roleIds = jdbcTemplate.query(
            "SELECT id FROM sys_role WHERE tenant_id = ? AND code = ? AND role_type = ? "
                + "AND status = ? AND is_deleted = ?",
            (resultSet, rowNum) -> resultSet.getLong("id"),
            tenantId,
            AdminConstants.SUPER_ADMIN_ROLE,
            AdminConstants.ROLE_TYPE_PLATFORM_SUPER,
            ENABLED,
            NOT_DELETED);
        if (roleIds.size() != 1) {
            throw new IllegalStateException("平台管理员角色必须存在且唯一，实际数量：" + roleIds.size());
        }
        return roleIds.getFirst();
    }

    private ExistingUser findUser(String username) {
        List<ExistingUser> users = jdbcTemplate.query(
            "SELECT id, tenant_id, user_type FROM sys_user WHERE username = ?",
            this::mapUser,
            username);
        if (users.size() > 1) {
            throw new IllegalStateException("平台管理员初始化账号存在重复数据：" + username);
        }
        return users.isEmpty() ? null : users.getFirst();
    }

    private ExistingUser mapUser(ResultSet resultSet, int rowNum) throws SQLException {
        return new ExistingUser(
            resultSet.getLong("id"),
            resultSet.getObject("tenant_id", Long.class),
            resultSet.getString("user_type"));
    }

    private void bindRole(long userId, long roleId) {
        jdbcTemplate.update(
            "INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_id = ?",
            Integer.class,
            userId,
            roleId);
        if (count == null || count != 1) {
            throw new IllegalStateException("平台管理员角色绑定失败");
        }
    }

    private long createUser(BootstrapInput input, String encodedPassword) {
        long userId = IdGenerator.nextId();
        int inserted = jdbcTemplate.update(
            "INSERT INTO sys_user (id, tenant_id, username, user_type, password, real_name, "
                + "create_time, update_time, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)",
            userId,
            input.tenantId(),
            input.username(),
            AdminConstants.USER_TYPE_PLATFORM,
            encodedPassword,
            input.realName(),
            ENABLED,
            NOT_DELETED);
        if (inserted != 1) {
            throw new IllegalStateException("平台管理员账号创建失败");
        }
        return userId;
    }

    private long enableUser(
        ExistingUser existingUser, BootstrapInput input, String encodedPassword) {
        if (!input.tenantId().equals(existingUser.tenantId())
            || !AdminConstants.USER_TYPE_PLATFORM.equals(existingUser.userType())) {
            throw new IllegalStateException("同名账号已存在，但不是目标租户的平台用户：" + input.username());
        }
        int updated = jdbcTemplate.update(
            "UPDATE sys_user SET password = ?, real_name = ?, status = ?, is_deleted = ?, "
                + "update_time = NOW() WHERE id = ?",
            encodedPassword,
            input.realName(),
            ENABLED,
            NOT_DELETED,
            existingUser.id());
        if (updated != 1) {
            throw new IllegalStateException("平台管理员账号更新失败");
        }
        return existingUser.id();
    }

    private record BootstrapInput(
        String username, String password, String realName, Long tenantId, String owner) {
    }

    private record ExistingUser(Long id, Long tenantId, String userType) {
    }
}
