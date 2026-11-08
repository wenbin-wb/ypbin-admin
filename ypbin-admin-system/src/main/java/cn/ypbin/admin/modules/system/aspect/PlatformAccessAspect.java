/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.aspect;

import cn.ypbin.admin.modules.system.annotation.PlatformAccess;
import cn.ypbin.admin.modules.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.security.core.LoginHelper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 平台用户访问守卫（单体版，基于本模块的 {@link PlatformAccess} 注解）。
 *
 * <p><b>为什么显式指定 Bean 名</b>：starter 4.4.0 的
 * {@code PlatformAccessAutoConfiguration} 也会注册一个名为 {@code platformAccessAspect} 的切面
 * （守卫 starter 的 {@code cn.ypbin.starter.security.platform.PlatformAccess}）。
 * 两者类型不同，{@code @ConditionalOnMissingBean}（按类型）不会让位，而默认 Bean 名相同，
 * 于是启动时报 {@code BeanDefinitionOverrideException}。单体没有使用 starter 的注解，但也不应
 * 因此关掉 starter 的自动配置（保持平台守卫能力在两种部署形态下都可用），故只给本切面改名，
 * 两个切面可以共存：本切面守卫单体注解，starter 切面守卫 starter 注解。</p>
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Aspect
@Component("adminPlatformAccessAspect")
@RequiredArgsConstructor
public class PlatformAccessAspect {

    private final SysPermissionService permissionService;

    @Before("@within(platformAccess)")
    public void guardClass(PlatformAccess platformAccess) {
        checkPlatformAccess();
    }

    @Before("@annotation(platformAccess)")
    public void guardMethod(PlatformAccess platformAccess) {
        checkPlatformAccess();
    }

    private void checkPlatformAccess() {
        Long userId = LoginHelper.getUserId();
        if (!permissionService.isPlatformUser(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "仅平台用户可访问");
        }
    }
}
