/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import cn.ypbin.admin.system.mapper.SysAuthTemplateMapper;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysTemplateMenuMapper;
import cn.ypbin.admin.system.mapper.SysTenantMapper;
import cn.ypbin.admin.system.model.req.AuthTemplateSaveReq;
import cn.ypbin.starter.core.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SysAuthTemplateServiceImpl} 菜单授权校验测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class SysAuthTemplateServiceImplTest {

    @Test
    void createTemplateRejectsNonPositiveMenuId() {
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysAuthTemplateServiceImpl service = new SysAuthTemplateServiceImpl(
            mock(SysTemplateMenuMapper.class), mock(SysTenantMapper.class), menuMapper);
        AuthTemplateSaveReq req = new AuthTemplateSaveReq();
        req.setName("租户模板");
        req.setCode("tenant");
        req.setMenuIds(List.of(0L));

        assertThatThrownBy(() -> service.createTemplate(req))
            .isInstanceOf(BusinessException.class)
            .hasMessage("权限模板菜单 ID 必须为正数");

        verifyNoInteractions(menuMapper);
    }
}
