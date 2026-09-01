/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.api.feign;

import cn.ypbin.starter.core.model.R;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 系统管理服务 Feign 客户端（供 auth-svc 等调用）。
 *
 * <p>内部调用由网关签发身份头，Feign 拦截器（starter-cloud-core）自动透传，
 * 服务端从 {@code X-User-Id} 等头识别调用者身份。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@FeignClient(name = "ypbin-system-service", path = "/internal")
public interface SystemPermissionFeignClient {

    /**
     * 查询用户权限码（供登录/鉴权使用）。
     *
     * @param userId 用户 ID
     * @return 权限码列表
     */
    @GetMapping("/permissions")
    R<List<String>> listPermissions(@RequestParam("userId") Long userId);

    /**
     * 查询用户角色标识（供登录/网关身份头使用）。
     *
     * @param userId 用户 ID
     * @return 角色码列表
     */
    @GetMapping("/role-codes")
    R<List<String>> listRoleCodes(@RequestParam("userId") Long userId);
}
