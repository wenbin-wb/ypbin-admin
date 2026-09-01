/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.api.feign;

import cn.ypbin.admin.system.entity.SysJob;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 系统管理服务 Feign 客户端（供 auth/ai 等调用）。
 *
 * <p>内部调用由网关签发身份头，Feign 拦截器（starter-cloud-core）自动透传，
 * 服务端从 {@code X-User-Id} 等头识别调用者身份。查询结果建议经
 * {@code SysCache} 缓存，避免高频 RPC。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@FeignClient(name = "ypbin-system", path = "/internal")
public interface SystemPermissionFeignClient {

    /**
     * 查询用户权限码。
     */
    @GetMapping("/permissions")
    R<List<String>> listPermissions(@RequestParam("userId") Long userId);

    /**
     * 查询用户角色码。
     */
    @GetMapping("/role-codes")
    R<List<String>> listRoleCodes(@RequestParam("userId") Long userId);

    /**
     * 按用户名查询用户（登录用）。
     */
    @GetMapping("/user-by-username")
    R<SysUser> getUserByUsername(@RequestParam("username") String username);

    /**
     * 按 ID 查询用户。
     */
    @GetMapping("/user-by-id")
    R<SysUser> getUserById(@RequestParam("userId") Long userId);

    /**
     * 按关键词搜索用户（AI 工具用，限制 10 条）。
     */
    @GetMapping("/search-users")
    R<List<SysUser>> searchUsers(@RequestParam("keyword") String keyword);

    /**
     * 按名称查询任务列表（AI 工具用，限制 20 条）。
     */
    @GetMapping("/list-jobs")
    R<List<SysJob>> listJobs(@RequestParam(value = "name", required = false) String name);

    /**
     * 用户计数与运行任务计数（AI 工具统计用）。
     */
    @GetMapping("/user-count")
    R<Long> countUsers();

    /**
     * 运行任务计数。
     */
    @GetMapping("/running-job-count")
    R<Long> countRunningJobs();
}
