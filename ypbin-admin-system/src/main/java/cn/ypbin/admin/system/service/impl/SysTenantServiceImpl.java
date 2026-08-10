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

import cn.ypbin.admin.system.entity.SysAuthTemplate;
import cn.ypbin.admin.system.entity.SysTenant;
import cn.ypbin.admin.system.mapper.SysTenantMapper;
import cn.ypbin.admin.system.model.req.TenantSaveReq;
import cn.ypbin.admin.system.model.resp.TenantResp;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.admin.system.service.SysTenantService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户服务实现。
 *
 * @author wenbin
 * @since 2026-08-09
 */
@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends BaseServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

    private final SysAuthTemplateService authTemplateService;

    @Override
    public List<TenantResp> listTenants() {
        return list(new LambdaQueryWrapper<SysTenant>().orderByDesc(SysTenant::getCreateTime))
            .stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTenant(TenantSaveReq req) {
        checkNameUnique(req.getName(), null);
        checkCodeUnique(req.getCode(), null);
        validateTemplate(req.getTemplateId());
        SysTenant tenant = new SysTenant();
        BeanUtils.copyProperties(req, tenant);
        if (!save(tenant)) {
            throw new BusinessException("租户创建失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTenant(Long id, TenantSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("租户不存在");
        }
        checkNameUnique(req.getName(), id);
        checkCodeUnique(req.getCode(), id);
        validateTemplate(req.getTemplateId());
        SysTenant tenant = new SysTenant();
        BeanUtils.copyProperties(req, tenant);
        tenant.setId(id);
        if (!updateById(tenant)) {
            throw new BusinessException("租户更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenant(Long id) {
        if (!removeById(id)) {
            throw new BusinessException("租户删除失败");
        }
    }

    private void checkNameUnique(String name, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysTenant>()
            .eq(SysTenant::getName, name)
            .ne(excludeId != null, SysTenant::getId, excludeId));
        if (exists) {
            throw new BusinessException("租户名称已存在：" + name);
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysTenant>()
            .eq(SysTenant::getCode, code)
            .ne(excludeId != null, SysTenant::getId, excludeId));
        if (exists) {
            throw new BusinessException("租户编码已存在：" + code);
        }
    }

    private void validateTemplate(Long templateId) {
        SysAuthTemplate template = authTemplateService.getById(templateId);
        if (template == null || !Integer.valueOf(1).equals(template.getStatus())) {
            throw new BusinessException("权限模板不存在或已禁用");
        }
    }

    private TenantResp toResp(SysTenant tenant) {
        TenantResp resp = new TenantResp();
        BeanUtils.copyProperties(tenant, resp);
        return resp;
    }
}
