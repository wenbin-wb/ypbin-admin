/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 公告投递目标解析器。
 *
 * <p>负责按全体、角色、部门、用户 ID 维度解析出目标用户列表，并完成多租户隔离与有效性校验。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@Component
public class NoticeTargetResolver {

    private static final int SCOPE_ALL = 1;
    private static final int SCOPE_ROLE = 2;
    private static final int SCOPE_DEPT = 3;
    private static final int SCOPE_USER = 4;

    private final SysUserService userService;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserRoleMapper userRoleMapper;

    public NoticeTargetResolver(SysUserService userService,
                                SysRoleMapper roleMapper,
                                SysDeptMapper deptMapper,
                                SysUserRoleMapper userRoleMapper) {
        this.userService = userService;
        this.roleMapper = roleMapper;
        this.deptMapper = deptMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /**
     * 解析公告目标用户列表。
     *
     * @param notice 公告实体
     * @return 目标用户列表
     */
    public List<SysUser> resolveTargets(SysNotice notice) {
        List<Long> targetIds = notice.getNoticeScope() == SCOPE_ALL
            ? List.of() : parseTargetIds(notice.getScopeTargetIds());
        List<SysUser> users = switch (notice.getNoticeScope()) {
            case SCOPE_ALL -> userService.list(enabledUsers());
            case SCOPE_ROLE -> resolveRoleUsers(notice, targetIds);
            case SCOPE_DEPT -> resolveDeptUsers(notice, targetIds);
            case SCOPE_USER -> userService.list(enabledUsers().in(SysUser::getId, targetIds));
            default -> throw new BusinessException("通知范围不合法");
        };
        if (notice.getNoticeScope() == SCOPE_USER && users.size() != new LinkedHashSet<>(targetIds).size()) {
            throw new BusinessException("指定用户不存在、已禁用或不属于当前租户");
        }
        return users;
    }

    private List<SysUser> resolveRoleUsers(SysNotice notice, List<Long> roleIds) {
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
            .in(SysRole::getId, roleIds).eq(SysRole::getStatus, 1));
        if (roles.size() != new LinkedHashSet<>(roleIds).size()
            || roles.stream().anyMatch(role -> !notice.getTenantId().equals(role.getTenantId()))) {
            throw new BusinessException("指定角色不存在、已禁用或不属于当前租户");
        }
        List<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getRoleId, roleIds))
            .stream().map(SysUserRole::getUserId).distinct().toList();
        return userIds.isEmpty() ? List.of()
            : userService.list(enabledUsers().in(SysUser::getId, userIds));
    }

    private List<SysUser> resolveDeptUsers(SysNotice notice, List<Long> deptIds) {
        List<SysDept> depts = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
            .in(SysDept::getId, deptIds).eq(SysDept::getStatus, 1));
        if (depts.size() != new LinkedHashSet<>(deptIds).size()
            || depts.stream().anyMatch(dept -> !notice.getTenantId().equals(dept.getTenantId()))) {
            throw new BusinessException("指定部门不存在、已禁用或不属于当前租户");
        }
        return userService.list(enabledUsers().in(SysUser::getDeptId, deptIds));
    }

    private LambdaQueryWrapper<SysUser> enabledUsers() {
        return new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1);
    }

    private List<Long> parseTargetIds(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new BusinessException("非全体通知必须选择目标");
        }
        try {
            List<Long> ids = Arrays.stream(csv.split(",", -1))
                .map(String::trim)
                .map(value -> {
                    if (value.isEmpty()) {
                        throw new BusinessException("通知目标包含空 ID");
                    }
                    return Long.valueOf(value);
                })
                .peek(id -> {
                    if (id <= 0) {
                        throw new BusinessException("通知目标 ID 必须为正数");
                    }
                })
                .distinct()
                .toList();
            if (ids.isEmpty()) {
                throw new BusinessException("通知目标不合法");
            }
            return ids;
        } catch (NumberFormatException e) {
            throw new BusinessException("通知目标包含非法 ID");
        }
    }
}
