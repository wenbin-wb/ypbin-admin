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
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 系统管理服务 Feign 接口（供 auth/ai 等调用）。
 *
 * <p>内部调用由网关签发身份头，Feign 拦截器（starter-cloud-core）自动透传，
 * 服务端从 {@code X-User-Id} 等头识别调用者身份。查询结果建议经
 * {@code SysCache} 缓存，避免高频 RPC。调用失败走 {@link ISystemClientFallback}
 * 降级返回失败 {@code R}。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@FeignClient(name = "ypbin-system", path = "/internal", fallback = ISystemClientFallback.class)
public interface ISystemClient {

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
     * 按手机号查询用户（手机验证码登录用）。
     */
    @GetMapping("/user-by-phone")
    R<SysUser> getUserByPhone(@RequestParam("phone") String phone);

    /**
     * 记录最后登录时间（登录成功收尾用）。
     */
    @GetMapping("/update-last-login")
    R<Void> updateLastLoginTime(@RequestParam("userId") Long userId);

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
     * 用户计数（AI 工具统计用）。
     */
    @GetMapping("/user-count")
    R<Long> countUsers();

    /**
     * 运行任务计数。
     */
    @GetMapping("/running-job-count")
    R<Long> countRunningJobs();

    /**
     * 按参数键读取系统参数（auth 读取登录开关/短信配置用）。
     */
    @GetMapping("/config-by-key")
    R<ConfigValue> getConfigByKey(@RequestParam("configKey") String configKey);

    /**
     * 第三方登录平台授权配置（auth 构建授权请求用，含密钥明文，仅限内部传递）。
     */
    @GetMapping("/social-auth-config")
    R<SocialAuthConfig> getSocialAuthConfig(@RequestParam("source") String source);

    /**
     * 全部第三方登录平台授权配置（auth 拉取启用平台列表用）。
     */
    @GetMapping("/social-auth-configs")
    R<List<SocialAuthConfig>> listSocialAuthConfigs();

    /**
     * 按平台与 openId 查绑定（第三方登录用）。
     */
    @GetMapping("/social-binding")
    R<SysUserSocial> getSocialBinding(@RequestParam("platform") String platform,
        @RequestParam("openId") String openId);

    /**
     * 用户是否已绑定指定平台。
     */
    @GetMapping("/social-user-bound")
    R<Boolean> isSocialUserBound(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform);

    /**
     * 按平台与 openId 是否已绑定其他用户。
     */
    @GetMapping("/social-account-bound")
    R<Boolean> isSocialAccountBound(@RequestParam("platform") String platform,
        @RequestParam("openId") String openId);

    /**
     * 新增第三方绑定。
     */
    @PostMapping("/social-bind-save")
    R<Void> saveSocialBinding(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform, @RequestParam("openId") String openId,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar,
        @RequestParam(value = "accessToken", required = false) String accessToken);

    /**
     * 解绑（按用户+平台）。
     */
    @PostMapping("/social-unbind")
    R<Void> unbindSocial(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform);

    /**
     * 用户已绑定的平台列表。
     */
    @GetMapping("/social-bindings")
    R<List<SysUserSocial>> listSocialBindings(@RequestParam("userId") Long userId);
}
