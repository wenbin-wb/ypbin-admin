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
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 系统管理服务 Feign 降级实现。
 *
 * <p>服务不可达或调用失败时返回失败 {@code R}（不静默吞错），调用方据此感知
 * 远程异常并给出提示。业务上可在降级时返回兜底数据，但不可返回成功态。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Component
public class ISystemClientFallback implements ISystemClient {

    private static <D> R<D> unavailable() {
        return R.fail(GlobalErrorCode.INTERNAL_ERROR.getCode(), "系统服务暂不可用，请稍后重试");
    }

    @Override
    public R<List<String>> listPermissions(Long userId) {
        return unavailable();
    }

    @Override
    public R<List<String>> listRoleCodes(Long userId) {
        return unavailable();
    }

    @Override
    public R<SysUser> getUserByUsername(String username) {
        return unavailable();
    }

    @Override
    public R<SysUser> getUserById(Long userId) {
        return unavailable();
    }

    @Override
    public R<SysUser> getUserByPhone(String phone) {
        return unavailable();
    }

    @Override
    public R<Void> updateLastLoginTime(Long userId) {
        return unavailable();
    }

    @Override
    public R<List<SysUser>> searchUsers(String keyword) {
        return unavailable();
    }

    @Override
    public R<List<SysJob>> listJobs(String name) {
        return unavailable();
    }

    @Override
    public R<Long> countUsers() {
        return unavailable();
    }

    @Override
    public R<Long> countRunningJobs() {
        return unavailable();
    }

    @Override
    public R<ConfigValue> getConfigByKey(String configKey) {
        return unavailable();
    }

    @Override
    public R<Boolean> verifyPassword(Long userId, String rawPassword) {
        return unavailable();
    }

    @Override
    public R<SocialAuthConfig> getSocialAuthConfig(String source) {
        return unavailable();
    }

    @Override
    public R<List<SocialAuthConfig>> listSocialAuthConfigs() {
        return unavailable();
    }

    @Override
    public R<SysUserSocial> getSocialBinding(String platform, String openId) {
        return unavailable();
    }

    @Override
    public R<Boolean> isSocialUserBound(Long userId, String platform) {
        return unavailable();
    }

    @Override
    public R<Boolean> isSocialAccountBound(String platform, String openId) {
        return unavailable();
    }

    @Override
    public R<Void> saveSocialBinding(Long userId, String platform, String openId,
        String nickname, String avatar, String accessToken) {
        return unavailable();
    }

    @Override
    public R<Void> unbindSocial(Long userId, String platform) {
        return unavailable();
    }

    @Override
    public R<List<SysUserSocial>> listSocialBindings(Long userId) {
        return unavailable();
    }
}
