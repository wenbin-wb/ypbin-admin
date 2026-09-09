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
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
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
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Override
    @GetMapping("/routes")
    public R<List<RouteResp>> listRoutes(@RequestParam("userId") Long userId) {
        return R.ok(menuService.buildRoutes(userId));
    }

    @Override
    @GetMapping("/user-by-username")
    public R<SysUser> getUserByUsername(@RequestParam("username") String username) {
        return R.ok(userService.getByUsername(username));
    }

    @Override
    @GetMapping("/user-by-id")
    public R<SysUser> getUserById(@RequestParam("userId") Long userId) {
        return R.ok(userService.getById(userId));
    }

    @Override
    @GetMapping("/user-by-phone")
    public R<SysUser> getUserByPhone(@RequestParam("phone") String phone) {
        return R.ok(userService.getByPhone(phone));
    }

    @Override
    @GetMapping("/update-last-login")
    public R<Void> updateLastLoginTime(@RequestParam("userId") Long userId) {
        userService.updateLastLoginTime(userId);
        return R.ok();
    }

    @Override
    @GetMapping("/search-users")
    public R<List<SysUser>> searchUsers(@RequestParam("keyword") String keyword) {
        return R.ok(userService.searchUsers(keyword));
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
    public R<SysUserSocial> getSocialBinding(@RequestParam("platform") String platform,
        @RequestParam("openId") String openId) {
        return R.ok(socialBindService.getByPlatformAndOpenId(platform, openId));
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
    public R<List<SysUserSocial>> listSocialBindings(@RequestParam("userId") Long userId) {
        return R.ok(socialBindService.listByUserId(userId));
    }

    @Override
    @PostMapping("/user-get-or-create-miniapp")
    public R<SysUser> getOrCreateMiniappUser(@RequestParam("username") String username,
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
        return R.ok(user);
    }

    @Override
    @PostMapping("/user-update-miniapp")
    public R<SysUser> updateMiniappUser(@RequestParam("userId") Long userId,
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
        return R.ok(user);
    }
}
