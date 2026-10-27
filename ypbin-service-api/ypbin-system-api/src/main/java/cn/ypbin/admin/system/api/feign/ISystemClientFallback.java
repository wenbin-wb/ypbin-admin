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

import cn.ypbin.admin.system.model.dto.SysSpaceDto;
import cn.ypbin.admin.system.model.dto.SysSpaceRelationDto;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import cn.ypbin.admin.system.model.req.InboxMessageSendReq;
import cn.ypbin.admin.system.model.req.MailSendReq;
import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.model.req.SpaceSyncReq;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.tracking.core.TrackEvent;
import java.util.List;
import java.util.Map;
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
    public R<List<SysUserDto>> searchUsers(String keyword, Long tenantId) {
        return unavailable();
    }

    @Override
    public R<Long> countUsers(Long tenantId) {
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

    @Override
    public R<SysUserDto> getOrCreateUserByUsername(String username, String nickname, String avatar,
        String userType, String defaultRealName) {
        return unavailable();
    }

    @Override
    public R<SysUserDto> updateUserProfile(Long userId, String nickname, String avatar, String phone) {
        return unavailable();
    }

    // ---------- 空间：一律返回失败 R，不静默降级 ----------
    //
    // 这里刻意不给任何「空空间/空列表」的兜底：空间查询失败若返回空列表，
    // 调用方（如 IoT 设备详情）会显示成「这台设备没有位置」——把「system 不可用」
    // 伪装成「业务事实」，运维永远查不出来。故一律走 unavailable()，由调用方上抛或显式提示。

    @Override
    public R<Void> sendInboxMessage(InboxMessageSendReq req) {
        return unavailable();
    }

    @Override
    public R<Void> sendMail(MailSendReq req) {
        return unavailable();
    }

    @Override
    public R<SysSpaceDto> getSpaceById(Long spaceId) {
        return unavailable();
    }

    @Override
    public R<List<SysSpaceDto>> listSpaces() {
        return unavailable();
    }

    @Override
    public R<Integer> syncSpaceRelations(SpaceSyncReq req) {
        return unavailable();
    }

    @Override
    public R<Map<Long, List<SysSpaceRelationDto>>> listSpaceRelationsByTargets(String targetType,
        List<Long> targetIds) {
        return unavailable();
    }

    @Override
    public R<List<SysSpaceRelationDto>> listSpaceRelationsByTarget(String targetType,
        Long targetId) {
        return unavailable();
    }

    @Override
    public R<List<Long>> listSpaceSubtreeIds(Long spaceId) {
        return unavailable();
    }

    @Override
    public R<List<Long>> listSpaceTargetIdsBySpace(Long spaceId, String targetType) {
        return unavailable();
    }

    @Override
    public R<Map<Long, Map<String, Long>>> countSpaceRelationsBySpaceTrees(List<Long> spaceIds) {
        return unavailable();
    }

    @Override
    public R<Integer> bindSpaceRelations(Long spaceId, String targetType, List<Long> targetIds,
        Integer isPrimary, Boolean clearOthers, String remark) {
        return unavailable();
    }

    @Override
    public R<Integer> unbindSpaceRelations(Long spaceId, String targetType,
        List<Long> targetIds) {
        return unavailable();
    }
}
