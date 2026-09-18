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

import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.tracking.core.TrackEvent;
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

    /**
     * 平台用户判定降级：返回失败 {@code R}（{@code code=500}）。
     *
     * <p>刻意不返回 {@code false}：判定器（{@code AiPlatformUserChecker}）据 {@code success=false}
     * 上抛异常并记完整堆栈。若在此返回「非平台用户」，一次 system 抖动就会被呈现成
     * 「仅平台用户可访问」这种正常业务结论，排查时毫无痕迹（禁静默降级）。</p>
     */
    @Override
    public R<Boolean> isPlatformUser(Long userId) {
        return unavailable();
    }

    @Override
    public R<List<RouteResp>> listRoutes(Long userId) {
        return unavailable();
    }

    @Override
    public R<SysUserDto> getUserByUsername(String username) {
        return unavailable();
    }

    @Override
    public R<SysUserDto> getUserById(Long userId) {
        return unavailable();
    }

    @Override
    public R<SysUserDto> getUserByPhone(String phone) {
        return unavailable();
    }

    @Override
    public R<Void> updateLastLoginTime(Long userId) {
        return unavailable();
    }

    @Override
    public R<List<SysUserDto>> searchUsers(String keyword) {
        return unavailable();
    }

    @Override
    public R<Long> countUsers() {
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
    public R<SysUserSocialDto> getSocialBinding(String platform, String openId) {
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
    public R<List<SysUserSocialDto>> listSocialBindings(Long userId) {
        return unavailable();
    }

    /**
     * 日志上报降级：返回失败 {@code R}（{@code code=500}）。
     *
     * <p>刻意不返回成功态：调用方（{@code RemoteLogDao}）据 {@code success=false} 上抛异常并记完整堆栈。
     * 若在此假装成功，"登录日志没落库"就成了无任何痕迹的静默丢失。</p>
     */
    @Override
    public R<Void> ingestLog(LogRecord logRecord) {
        return unavailable();
    }

    /**
     * 埋点上报降级：返回失败 {@code R}（{@code code=500}）。
     *
     * <p>同样刻意不返回成功态：调用方（{@code RemoteTrackEventSink}）据 {@code success=false}
     * 记完整堆栈——若在此假装成功，「登录事件没落库」就成了无任何痕迹的静默丢失。</p>
     */
    @Override
    public R<Void> ingestTrackEvents(List<TrackEvent> events) {
        return unavailable();
    }
}
