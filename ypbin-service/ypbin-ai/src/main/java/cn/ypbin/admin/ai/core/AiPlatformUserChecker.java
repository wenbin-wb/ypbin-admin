/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.core;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.platform.PlatformUserChecker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ai 服务的平台用户判定器（starter 端口 {@link PlatformUserChecker} 的宿主实现）。
 *
 * <p><strong>为什么必须有本类</strong>：starter 的 {@code PlatformAccessAutoConfiguration}
 * 在宿主未提供判定器时装配一个判定恒为否的默认实现（fail-closed），于是 ai 侧
 * {@code @PlatformAccess} 标注的接口（如 {@code AiModelConfigController}）在任何情况下都返回
 * 403「仅平台用户可访问」——不是权限配错，而是判定器根本没实现。</p>
 *
 * <p><strong>为什么走 Feign 而不直连 {@code sys_user}</strong>：本仓约定跨服务禁止调用方直连共享库
 * （否则同一份「平台用户」口径会在多个服务里各写一遍而漂移），且 ai 的 Nacos 配置
 * （{@code deploy/nacos/ypbin-ai.yaml}）没有 {@code ypbin.tenant.ignore-tables}，
 * 直连共享表会被租户插件追加 {@code tenant_id} 条件而查不到。故判定语义只有 system 一份实现
 * （{@code SysPermissionServiceImpl#isPlatformUser}），本类经
 * {@link ISystemClient#isPlatformUser(Long)} 复用；该端点内部已
 * {@code TenantContext.executeIgnore}，与调用方有无租户上下文无关。</p>
 *
 * <p><strong>失败语义</strong>：system 不可达（Feign 降级返回失败 {@code R}）或用户身份缺失时，
 * <b>不让请求通过</b>（fail-closed），但也不把故障伪装成「非平台用户」的 403——而是记完整上下文后
 * 抛出显式的系统错误，让运维一眼看出是依赖故障而非权限问题（禁静默降级）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
@RequiredArgsConstructor
public class AiPlatformUserChecker implements PlatformUserChecker {

    private static final Logger log = LoggerFactory.getLogger(AiPlatformUserChecker.class);

    private final ISystemClient systemClient;

    /**
     * 判定指定用户是否为平台用户。
     *
     * @param userId 用户 ID（{@code PlatformAccessAspect} 已在调用前拒绝未登录，正常不为空）
     * @return 是平台用户返回 {@code true}
     * @throws BusinessException 用户身份缺失，或 system 判定不可达/失败时（两者都不放行）
     */
    @Override
    public boolean isPlatformUser(Long userId) {
        if (userId == null) {
            // 契约上调用方负责判空；真出现则显式拒绝，既不静默当成「非平台用户」，也不放行
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "平台用户判定失败：用户身份缺失");
        }
        R<Boolean> response = systemClient.isPlatformUser(userId);
        Boolean platformUser = response == null ? null : response.getData();
        if (response == null || !response.isSuccess() || platformUser == null) {
            int code = response == null ? GlobalErrorCode.INTERNAL_ERROR.getCode() : response.getCode();
            String message = response == null ? "响应为空" : response.getMessage();
            log.error("[ai] 平台用户判定不可达，已按拒绝处理（未放行）：userId={}, code={}, message={}",
                userId, code, message);
            throw new BusinessException(GlobalErrorCode.INTERNAL_ERROR, "平台用户判定失败，请稍后重试");
        }
        return platformUser;
    }
}
