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
import cn.ypbin.admin.system.entity.SysJob;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.enums.JobStatusEnum;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.mapper.SysConfigMapper;
import cn.ypbin.admin.system.mapper.SysJobMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.service.SocialBindService;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.social.SocialConfigReader;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    private final SysPermissionService permissionService;
    private final SysUserService userService;
    private final SysUserMapper userMapper;
    private final SysJobMapper jobMapper;
    private final SysConfigMapper configMapper;
    private final SysConfigService configService;
    private final SocialConfigReader socialConfigReader;
    private final SocialBindService socialBindService;

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
    @GetMapping("/user-by-username")
    public R<SysUser> getUserByUsername(@RequestParam("username") String username) {
        return R.ok(userService.getByUsername(username));
    }

    @Override
    @GetMapping("/user-by-id")
    public R<SysUser> getUserById(@RequestParam("userId") Long userId) {
        return R.ok(userMapper.selectById(userId));
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
        return R.ok(userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .like(SysUser::getUsername, keyword)
            .or()
            .like(SysUser::getRealName, keyword)
            .last("LIMIT 10")));
    }

    @Override
    @GetMapping("/list-jobs")
    public R<List<SysJob>> listJobs(@RequestParam(value = "name", required = false) String name) {
        return R.ok(jobMapper.selectList(new LambdaQueryWrapper<SysJob>()
            .like(name != null && !name.isBlank(), SysJob::getName, name)
            .last("LIMIT 20")));
    }

    @Override
    @GetMapping("/user-count")
    public R<Long> countUsers() {
        return R.ok(userMapper.selectCount(null));
    }

    @Override
    @GetMapping("/running-job-count")
    public R<Long> countRunningJobs() {
        return R.ok(jobMapper.selectCount(new LambdaQueryWrapper<SysJob>()
            .eq(SysJob::getStatus, JobStatusEnum.ENABLED.getCode())));
    }

    @Override
    @GetMapping("/config-by-key")
    public R<ConfigValue> getConfigByKey(@RequestParam("configKey") String configKey) {
        ConfigValue value = new ConfigValue();
        value.setConfigKey(configKey);
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
            .eq(SysConfig::getConfigKey, configKey), false);
        value.setConfigValue(config == null ? "" : config.getConfigValue());
        return R.ok(value);
    }

    @Override
    @PostMapping("/verify-password")
    public R<Boolean> verifyPassword(@RequestParam("userId") Long userId,
        @RequestParam("rawPassword") String rawPassword) {
        SysUser user = TenantContext.executeIgnore(() -> userMapper.selectById(userId));
        if (user == null || user.getPassword() == null) {
            return R.ok(false);
        }
        return R.ok(PasswordEncoderUtil.matches(rawPassword, user.getPassword()));
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
}
