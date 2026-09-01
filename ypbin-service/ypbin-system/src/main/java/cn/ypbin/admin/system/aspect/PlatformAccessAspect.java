/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.aspect;

import cn.ypbin.admin.common.PlatformAccess;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.security.core.LoginHelper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 平台用户访问守卫。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Aspect
@Component
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
