/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import cn.ypbin.admin.system.entity.SysJob;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.enums.JobStatusEnum;
import cn.ypbin.admin.system.mapper.SysJobMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理服务内部接口（Feign 专用）。
 *
 * <p>仅服务间调用使用（经网关内网），不对外暴露；供 auth/ai 经
 * {@code SystemPermissionFeignClient} 查询权限、角色与用户信息。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalPermissionController {

    private final SysPermissionService permissionService;
    private final SysUserMapper userMapper;
    private final SysJobMapper jobMapper;

    /**
     * 查询用户权限码。
     */
    @GetMapping("/permissions")
    public R<List<String>> listPermissions(@RequestParam("userId") Long userId) {
        return R.ok(permissionService.listPermissions(userId));
    }

    /**
     * 查询用户角色码。
     */
    @GetMapping("/role-codes")
    public R<List<String>> listRoleCodes(@RequestParam("userId") Long userId) {
        return R.ok(permissionService.listRoleCodes(userId));
    }

    /**
     * 按用户名查询用户（登录用）。
     */
    @GetMapping("/user-by-username")
    public R<SysUser> getUserByUsername(@RequestParam("username") String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username), false);
        return R.ok(user);
    }

    /**
     * 按 ID 查询用户。
     */
    @GetMapping("/user-by-id")
    public R<SysUser> getUserById(@RequestParam("userId") Long userId) {
        return R.ok(userMapper.selectById(userId));
    }

    /**
     * 按关键词搜索用户（AI 工具用，限制 10 条）。
     */
    @GetMapping("/search-users")
    public R<List<SysUser>> searchUsers(@RequestParam("keyword") String keyword) {
        return R.ok(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .like(SysUser::getUsername, keyword)
            .or()
            .like(SysUser::getRealName, keyword)
            .last("LIMIT 10")));
    }

    /**
     * 按名称查询任务列表（AI 工具用，限制 20 条）。
     */
    @GetMapping("/list-jobs")
    public R<List<SysJob>> listJobs(@RequestParam(value = "name", required = false) String name) {
        return R.ok(jobMapper.selectList(new LambdaQueryWrapper<SysJob>()
            .like(name != null && !name.isBlank(), SysJob::getName, name)
            .last("LIMIT 20")));
    }

    /**
     * 用户计数（AI 工具统计用）。
     */
    @GetMapping("/user-count")
    public R<Long> countUsers() {
        return R.ok(userMapper.selectCount(null));
    }

    /**
     * 运行任务计数。
     */
    @GetMapping("/running-job-count")
    public R<Long> countRunningJobs() {
        return R.ok(jobMapper.selectCount(new LambdaQueryWrapper<SysJob>()
            .eq(SysJob::getStatus, JobStatusEnum.ENABLED.getCode())));
    }
}
