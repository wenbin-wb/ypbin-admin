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

import cn.ypbin.admin.system.api.feign.config.InternalTokenFeignConfiguration;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.tracking.core.TrackEvent;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@FeignClient(name = "ypbin-system", path = "/internal",
    configuration = InternalTokenFeignConfiguration.class,
    fallback = ISystemClientFallback.class)
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
     * 判定用户是否为平台用户（{@code PLATFORM} 类型、启用且未逻辑删除）。
     *
     * <p>供各服务实现 starter 的 {@code PlatformUserChecker} 端口用：{@code @PlatformAccess}
     * 标注的资源在宿主未提供判定器时一律拒绝（fail-closed），而调用方服务禁止直连共享库，
     * 故判定口径只有 system 一份实现（{@code SysPermissionServiceImpl}），调用方经本端点复用，
     * 避免同一规则在多服务里各写一遍而产生漂移。</p>
     *
     * <p>失败语义：system 不可达时走 {@link ISystemClientFallback} 返回失败 {@code R}，
     * 调用方必须据 {@code R.success}/{@code R.code} 判定并上抛异常（禁止静默当作「非平台用户」）。</p>
     */
    @GetMapping("/platform-user")
    R<Boolean> isPlatformUser(@RequestParam("userId") Long userId);

    /**
     * 查询用户可访问的路由树（登录后动态菜单）。
     */
    @GetMapping("/routes")
    R<List<RouteResp>> listRoutes(@RequestParam("userId") Long userId);

    /**
     * 按用户名查询用户（登录用）。
     */
    @GetMapping("/user-by-username")
    R<SysUserDto> getUserByUsername(@RequestParam("username") String username);

    /**
     * 按 ID 查询用户。
     */
    @GetMapping("/user-by-id")
    R<SysUserDto> getUserById(@RequestParam("userId") Long userId);

    /**
     * 按手机号查询用户（手机验证码登录用）。
     */
    @GetMapping("/user-by-phone")
    R<SysUserDto> getUserByPhone(@RequestParam("phone") String phone);

    /**
     * 记录最后登录时间（登录成功收尾用）。
     */
    @GetMapping("/update-last-login")
    R<Void> updateLastLoginTime(@RequestParam("userId") Long userId);

    /**
     * 按关键词搜索用户（AI 工具用，限制 10 条）。
     */
    @GetMapping("/search-users")
    R<List<SysUserDto>> searchUsers(@RequestParam("keyword") String keyword);

    /**
     * 用户计数（AI 工具统计用）。
     */
    @GetMapping("/user-count")
    R<Long> countUsers();

    /**
     * 按参数键读取系统参数（auth 读取登录开关/短信配置用）。
     */
    @GetMapping("/config-by-key")
    R<ConfigValue> getConfigByKey(@RequestParam("configKey") String configKey);

    /**
     * 密码校验（system 直查库比对；密码不落缓存，改密即时生效）。
     */
    @PostMapping("/verify-password")
    R<Boolean> verifyPassword(@RequestParam("userId") Long userId,
        @RequestParam("rawPassword") String rawPassword);

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
    R<SysUserSocialDto> getSocialBinding(@RequestParam("platform") String platform,
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
    R<List<SysUserSocialDto>> listSocialBindings(@RequestParam("userId") Long userId);

    /**
     * 上报一条操作/登录日志（auth、ai 等无数据源的调用方专用）。
     *
     * <p>上报的是采集完成的 {@link LogRecord} 本身，system 侧直接交给既有的
     * {@code LogDao}（{@code DbLogProviders.DbLogDao}）落 {@code sys_log}；
     * 调用方不得再自建一套字段映射，否则同一张表会有两份口径。</p>
     *
     * <p>失败语义：system 不可达时走 {@link ISystemClientFallback} 返回失败 {@code R}，
     * 调用方必须据 {@code R.success}/{@code R.code} 判定并上抛异常（禁止静默丢弃），
     * 由调用侧记完整堆栈。</p>
     */
    @PostMapping("/log-ingest")
    R<Void> ingestLog(@RequestBody LogRecord logRecord);

    /**
     * 上报一批后端业务埋点事件（auth 等无埋点落库能力的调用方专用）。
     *
     * <p>承载的是 starter 采集模型 {@link TrackEvent} 本身，system 侧直接交给其容器内的
     * {@code TrackRecorder}（唯一写入口，未登记事件码在此被拒绝），因此「事件码登记校验 →
     * 有界队列 → 消费者线程 → {@code sys_track_event} 落库」全仓只有一份实现；
     * 调用方只是搬运，不做任何字段改名映射。</p>
     *
     * <p>失败语义：system 不可达或埋点未启用时返回失败 {@code R}，调用方必须据
     * {@code R.success}/{@code R.code} 判定并记完整堆栈（禁止静默丢弃）。</p>
     *
     * @param events 待落库事件；调用方须已在请求线程上捕获 IP/UA/链路 ID/用户等上下文
     * @return 统一响应体
     */
    @PostMapping("/track-ingest")
    R<Void> ingestTrackEvents(@RequestBody List<TrackEvent> events);

    /**
     * 按用户名查询或创建小程序用户（微信小程序登录用）。
     *
     * <p>返回只读视图 {@link SysUserDto}（与其余用户查询一致，不暴露持久化实体）。</p>
     */
    @PostMapping("/user-get-or-create-miniapp")
    R<SysUserDto> getOrCreateMiniappUser(@RequestParam("username") String username,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar);

    /**
     * 更新小程序用户信息（昵称、头像、手机号）。
     */
    @PostMapping("/user-update-miniapp")
    R<SysUserDto> updateMiniappUser(@RequestParam("userId") Long userId,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar,
        @RequestParam(value = "phone", required = false) String phone);
}
