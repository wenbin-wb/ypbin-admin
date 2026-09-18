/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.feign;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysConfig;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.feign.support.UserViewConverter;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.service.SysMenuService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.starter.cache.util.CacheUtils;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.util.UUID;
import org.springframework.util.StringUtils;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理服务 Feign 接口实现（内部端点）。
 *
 * <p>仅服务间调用使用（经网关内网），不对外暴露；供 auth/ai 经
 * {@link ISystemClient} 查询权限、角色、用户信息与系统参数。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class SystemClientImpl implements ISystemClient {

    /** 密码校验限频键前缀（按用户维度，防任意 userId 在线口令爆破） */
    private static final String VERIFY_PASSWORD_LIMIT_KEY = "internal:verify:";

    /** 密码校验每分钟允许的最大尝试次数 */
    private static final long VERIFY_PASSWORD_MAX_PER_MINUTE = 10L;

    /** 密码校验限频窗口时长 */
    private static final Duration VERIFY_PASSWORD_WINDOW = Duration.ofMinutes(1);

    /** 整键命中即脱敏的系统参数（与列表页掩码口径一致，防密钥经 internal 出网） */
    private static final List<String> SENSITIVE_CONFIG_SUFFIXES = List.of(
        "_SECRET", "_PASSWORD", "_TOKEN", "_ACCESS_KEY", "_PRIVATE_KEY", "_API_KEY");

    /** 打码符 */
    private static final String MASK_PREFIX = "****";

    private final SysPermissionService permissionService;
    private final SysUserService userService;
    private final SysConfigMapper configMapper;
    private final SocialConfigReader socialConfigReader;
    private final SocialBindService socialBindService;
    private final SysMenuService menuService;
    /** 日志落库端口：system 侧由 {@code DbLogProviders.DbLogDao} 提供，本类只做路由不做映射 */
    private final LogDao logDao;

    /**
     * 埋点采集门面（可选）：只有 {@code ypbin.tracking.enabled=true} 时 starter 才装配它。
     *
     * <p>刻意用 {@link ObjectProvider} 而不是直接注入：埋点开关关闭时本 Bean 不存在，
     * 直接注入会让整个 system 服务启动失败（把「可选能力」变成「启动硬依赖」）。
     * 但取不到时也<b>不能静默丢弃</b>事件——那样上报方会以为成功，故显式失败返回（见方法体）。</p>
     */
    private final ObjectProvider<TrackRecorder> trackRecorderProvider;

    @Override
    @GetMapping("/permissions")
    public R<List<String>> listPermissions(@RequestParam("userId") Long userId) {
        return R.ok(permissionService.listPermissions(userId));
    }

    @Override
    @GetMapping("/role-codes")
    public R<List<String>> listRoleCodes(@RequestParam("userId") Long userId) {
        return R.ok(permissionService.listRoleCodes(userId));
    }

    /**
     * 平台用户判定（供 ai 的 {@code PlatformUserChecker} 实现复用）。
     *
     * <p>直接委托 {@code SysPermissionService#isPlatformUser}——判定口径（用户类型/启用/未删除）
     * 与租户忽略（该方法内部 {@code TenantContext.executeIgnore}）都在 service 一处，本端点只做路由，
     * 不在传输层重写一遍查询条件。</p>
     *
     * @param userId 用户 ID
     * @return 是平台用户返回 {@code true} 的统一响应体
     */
    @Override
    @GetMapping("/platform-user")
    public R<Boolean> isPlatformUser(@RequestParam("userId") Long userId) {
        return R.ok(permissionService.isPlatformUser(userId));
    }

    @Override
    @GetMapping("/routes")
    public R<List<RouteResp>> listRoutes(@RequestParam("userId") Long userId) {
        return R.ok(menuService.buildRoutes(userId));
    }

    @Override
    @GetMapping("/user-by-username")
    public R<SysUserDto> getUserByUsername(@RequestParam("username") String username) {
        return R.ok(UserViewConverter.toDto(userService.getByUsername(username)));
    }

    /**
     * 按 ID 取用户（供 auth 的第三方回调等<b>匿名</b>链路使用）。
     *
     * <p>匿名链路必然没有网关签发的 {@code X-Tenant-Id}（{@code SaTokenGatewayAuthProvider}
     * 仅在登录后签发），而 {@code /auth/social/callback/**} 又在网关白名单内，故这里必须走
     * {@code getByIdGlobal}（内部 {@code TenantContext.executeIgnore}）——否则租户拦截器
     * fail-closed 会抛「缺少租户上下文」，第三方登录直接失败。与同文件其余 6 个端点一致：
     * 租户忽略留在 service 一处，本端点只做路由。</p>
     *
     * @param userId 用户 ID
     * @return 用户统一响应体
     */
    @Override
    @GetMapping("/user-by-id")
    public R<SysUserDto> getUserById(@RequestParam("userId") Long userId) {
        return R.ok(UserViewConverter.toDto(userService.getByIdGlobal(userId)));
    }

    @Override
    @GetMapping("/user-by-phone")
    public R<SysUserDto> getUserByPhone(@RequestParam("phone") String phone) {
        return R.ok(UserViewConverter.toDto(userService.getByPhone(phone)));
    }

    @Override
    @GetMapping("/update-last-login")
    public R<Void> updateLastLoginTime(@RequestParam("userId") Long userId) {
        userService.updateLastLoginTime(userId);
        return R.ok();
    }

    @Override
    @GetMapping("/search-users")
    public R<List<SysUserDto>> searchUsers(@RequestParam("keyword") String keyword) {
        return R.ok(UserViewConverter.toUserDtoList(userService.searchUsers(keyword)));
    }

    @Override
    @GetMapping("/user-count")
    public R<Long> countUsers() {
        return R.ok(userService.countUsers());
    }

    @Override
    @GetMapping("/config-by-key")
    public R<ConfigValue> getConfigByKey(@RequestParam("configKey") String configKey) {
        ConfigValue value = new ConfigValue();
        value.setConfigKey(configKey);
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigKey, configKey), false);
        String rawValue = config == null ? "" : config.getConfigValue();
        // 密钥/口令类参数禁止经 internal 出网（纵深防御：即使凭证被泄露，密钥也不得明文返回）。
        // 已核验 auth 经本接口仅读取非敏感键（LOGIN_*/SMS_CODE_*/SMS_TEMPLATE_ID），不受影响；
        // 短信/邮件等密钥由 system 本地缓存直读，不走本接口。
        value.setConfigValue(maskIfSensitive(configKey, rawValue));
        return R.ok(value);
    }

    @Override
    @PostMapping("/verify-password")
    public R<Boolean> verifyPassword(@RequestParam("userId") Long userId,
        @RequestParam("rawPassword") String rawPassword) {
        requireVerifyNotExceeded(userId);
        return R.ok(userService.verifyPassword(userId, rawPassword));
    }

    /**
     * 密码校验频控：INCR + 首次设置过期时间。INCR 与 EXPIRE 非原子，
     * 极端并发下可能少设一次过期（键更早过期，窗口变短），仅放宽限制方向，
     * 不会出现超窗累积，可接受；不做 Lua 以保持最小改动。
     *
     * @param userId 用户 ID
     */
    private void requireVerifyNotExceeded(Long userId) {
        String key = VERIFY_PASSWORD_LIMIT_KEY + userId;
        long attempts = CacheUtils.increment(key, 1);
        if (attempts == 1L) {
            CacheUtils.expire(key, VERIFY_PASSWORD_WINDOW);
        }
        if (attempts > VERIFY_PASSWORD_MAX_PER_MINUTE) {
            throw new BusinessException(GlobalErrorCode.TOO_MANY_REQUESTS,
                "密码校验过于频繁，请稍后重试");
        }
    }

    /**
     * 系统参数键命中敏感后缀时对值脱敏（仅保留末 4 位，其余打码）。
     *
     * @param configKey 参数键
     * @param rawValue  原始值
     * @return 脱敏后的值（非敏感键原样返回）
     */
    private static String maskIfSensitive(String configKey, String rawValue) {
        if (configKey == null || rawValue == null || rawValue.isBlank()) {
            return rawValue;
        }
        String upperKey = configKey.toUpperCase(Locale.ROOT);
        boolean sensitive = SENSITIVE_CONFIG_SUFFIXES.stream().anyMatch(upperKey::endsWith);
        if (!sensitive) {
            return rawValue;
        }
        return rawValue.length() <= 4 ? MASK_PREFIX
            : MASK_PREFIX + rawValue.substring(rawValue.length() - 4);
    }

    @Override
    @GetMapping("/social-auth-config")
    public R<SocialAuthConfig> getSocialAuthConfig(@RequestParam("source") String source) {
        return R.ok(socialConfigReader.read(source));
    }

    @Override
    @GetMapping("/social-auth-configs")
    public R<List<SocialAuthConfig>> listSocialAuthConfigs() {
        return R.ok(socialConfigReader.listEnabled());
    }

    @Override
    @GetMapping("/social-binding")
    public R<SysUserSocialDto> getSocialBinding(@RequestParam("platform") String platform,
        @RequestParam("openId") String openId) {
        return R.ok(UserViewConverter.toDto(socialBindService.getByPlatformAndOpenId(platform, openId)));
    }

    @Override
    @GetMapping("/social-user-bound")
    public R<Boolean> isSocialUserBound(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform) {
        return R.ok(socialBindService.isUserBound(userId, platform));
    }

    @Override
    @GetMapping("/social-account-bound")
    public R<Boolean> isSocialAccountBound(@RequestParam("platform") String platform,
        @RequestParam("openId") String openId) {
        return R.ok(socialBindService.isAccountBound(platform, openId));
    }

    @Override
    @PostMapping("/social-bind-save")
    public R<Void> saveSocialBinding(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform, @RequestParam("openId") String openId,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar,
        @RequestParam(value = "accessToken", required = false) String accessToken) {
        SysUserSocial social = new SysUserSocial();
        social.setUserId(userId);
        social.setPlatform(platform);
        social.setOpenId(openId);
        social.setNickname(nickname);
        social.setAvatar(avatar);
        social.setAccessToken(accessToken);
        socialBindService.save(social);
        return R.ok();
    }

    @Override
    @PostMapping("/social-unbind")
    public R<Void> unbindSocial(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform) {
        socialBindService.unbind(userId, platform);
        return R.ok();
    }

    @Override
    @GetMapping("/social-bindings")
    public R<List<SysUserSocialDto>> listSocialBindings(@RequestParam("userId") Long userId) {
        return R.ok(UserViewConverter.toSocialDtoList(socialBindService.listByUserId(userId)));
    }

    /**
     * 接收 auth/ai 上报的操作/登录日志并落 {@code sys_log}。
     *
     * <p>直接委托容器内的 {@link LogDao}（system 侧为 {@code DbLogProviders.DbLogDao}），
     * 因此 {@code LogRecord → sys_log} 的字段映射全仓只有一份，本端点不做任何二次映射。
     * 刻意不加 {@code @Log}：否则上报一条日志会再触发一条日志采集，形成自反馈放大。</p>
     *
     * <p>异常不吞：{@code LogDao} 落库失败直接上抛，由全局异常处理器转成 HTTP 200 + {@code R.code}，
     * 调用方（{@code RemoteLogDao}）据 {@code success=false} 上抛并记完整堆栈。</p>
     */
    @Override
    @PostMapping("/log-ingest")
    public R<Void> ingestLog(@RequestBody LogRecord logRecord) {
        logDao.add(logRecord);
        return R.ok();
    }

    /**
     * 接收 auth 等无落库能力的服务上报的后端业务埋点事件。
     *
     * <p>只把事件交给容器内的 {@link TrackRecorder}（starter 的唯一写入口，未登记事件码在此被拒绝），
     * 因此「事件码登记校验 → 有界队列 → 消费者线程 → {@code SysTrackEventSink} 落库」全仓只有一份实现，
     * 本端点不做任何二次映射。刻意不加 {@code @Log}：否则一次埋点上报会再触发一条操作日志采集。</p>
     *
     * <p>异常不吞：埋点未启用时显式抛错（{@code R.success=false}），由调用方记完整堆栈——
     * 若静默返回成功，「登录事件没落库」将没有任何痕迹。</p>
     */
    @Override
    @PostMapping("/track-ingest")
    public R<Void> ingestTrackEvents(@RequestBody List<TrackEvent> events) {
        TrackRecorder recorder = trackRecorderProvider.getIfAvailable();
        if (recorder == null) {
            throw new BusinessException("埋点未启用，无法接收事件上报（请检查 ypbin.tracking.enabled）");
        }
        recorder.record(events);
        return R.ok();
    }

    @Override
    @PostMapping("/user-get-or-create-miniapp")
    public R<SysUserDto> getOrCreateMiniappUser(@RequestParam("username") String username,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar) {
        SysUser user = userService.getOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username));
        if (user == null) {
            user = new SysUser();
            user.setUsername(username);
            user.setRealName(StringUtils.hasText(nickname) ? nickname : "微信用户");
            user.setNickname(nickname);
            user.setAvatar(avatar);
            user.setUserType("MINIAPP");
            user.setPassword(PasswordEncoderUtil.encode(UUID.randomUUID().toString()));
            user.setStatus(1);
            userService.save(user);
        } else {
            boolean updated = false;
            if (StringUtils.hasText(nickname) && !nickname.equals(user.getNickname())) {
                user.setNickname(nickname);
                user.setRealName(nickname);
                updated = true;
            }
            if (StringUtils.hasText(avatar) && !avatar.equals(user.getAvatar())) {
                user.setAvatar(avatar);
                updated = true;
            }
            if (updated) {
                userService.updateById(user);
            }
        }
        return R.ok(UserViewConverter.toDto(user));
    }

    @Override
    @PostMapping("/user-update-miniapp")
    public R<SysUserDto> updateMiniappUser(@RequestParam("userId") Long userId,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar,
        @RequestParam(value = "phone", required = false) String phone) {
        SysUser user = userService.getById(userId);
        if (user == null) {
            return R.fail("用户不存在");
        }
        boolean updated = false;
        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname.trim());
            user.setRealName(nickname.trim());
            updated = true;
        }
        if (StringUtils.hasText(avatar)) {
            user.setAvatar(avatar.trim());
            updated = true;
        }
        if (StringUtils.hasText(phone)) {
            user.setPhone(phone.trim());
            updated = true;
        }
        if (updated) {
            userService.updateById(user);
        }
        return R.ok(UserViewConverter.toDto(user));
    }
}
