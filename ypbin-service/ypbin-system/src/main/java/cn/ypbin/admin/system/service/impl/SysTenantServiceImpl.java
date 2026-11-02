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

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysAuthTemplate;
import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysPost;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysTenant;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysTenantMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.model.req.TenantSaveReq;
import cn.ypbin.admin.system.model.resp.TenantResp;
import cn.ypbin.admin.system.service.SysAuthTemplateService;
import cn.ypbin.admin.system.service.SysTenantService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SysTenantServiceImpl.class);

    private final SysAuthTemplateService authTemplateService;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;
    private final OnlineUserService onlineUserService;

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
        SysTenant existing = getById(id);
        if (existing == null) {
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
        boolean disabledNow = Integer.valueOf(0).equals(req.getStatus())
            && !Integer.valueOf(0).equals(existing.getStatus());
        if (disabledNow || !Objects.equals(existing.getTemplateId(), req.getTemplateId())) {
            // 重绑模板或禁用租户后，租户下用户的角色/权限缓存（永久键）都必须批量失效
            List<Long> userIds = evictTenantUsers(id);
            if (disabledNow) {
                // 缓存失效不够：已建立的 Sa-Token 会话仍然有效，必须把在线用户踢下线
                kickoutTenantUsers(id, userIds);
            }
        }
    }

    /**
     * 踢掉指定租户用户的在线会话。
     *
     * <p>单个用户踢下线失败不阻断其余用户（Redis 抖动时仍尽量收敛暴露面），但必须记 error 日志。</p>
     *
     * @param tenantId 租户 ID
     * @param userIds  该租户的用户 ID
     */
    private void kickoutTenantUsers(Long tenantId, List<Long> userIds) {
        for (Long userId : userIds) {
            try {
                onlineUserService.kickoutByUserId(userId);
            } catch (RuntimeException e) {
                log.error("租户禁用后踢下线失败：tenantId={}, userId={}", tenantId, userId, e);
            }
        }
        log.info("租户已禁用：权限缓存已失效并踢下线，tenantId={}, users={}",
            tenantId, userIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTenant(Long id) {
        SysTenant tenant = getById(id);
        if (tenant == null) {
            throw new BusinessException("租户不存在");
        }
        if (Long.valueOf(1L).equals(id)) {
            throw new BusinessException("内置默认租户不允许删除");
        }
        // 删除前校验租户下无业务数据，避免产生孤儿数据
        long userCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getTenantId, id));
        long roleCount = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getTenantId, id));
        long deptCount = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getTenantId, id));
        long postCount = postMapper.selectCount(new LambdaQueryWrapper<SysPost>().eq(SysPost::getTenantId, id));
        if (userCount + roleCount + deptCount + postCount > 0) {
            throw new BusinessException("该租户下存在用户/角色/部门/岗位数据，请先清空后再删除");
        }
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

    /**
     * 租户重绑权限模板后，清理该租户下全部用户的角色/权限缓存。
     *
     * <p>与权限模板变更清缓存同款：角色/权限缓存为永久键，重绑后解析结果随新模板变化；
     * 按租户批量查用户 ID（两步查询，禁循环内查库），一次性批量失效。</p>
     *
     * @param tenantId 租户 ID
     */
    private List<Long> evictTenantUsers(Long tenantId) {
        List<SysUser> users = TenantContext.executeIgnore(() -> userMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .select(SysUser::getId)));
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(SysUser::getId).distinct().toList();
        // 批版接口合并键后一次删除；逐个 forEach 会让 N 个用户变成 N 次缓存往返
        SysCache.evictUserAuth(userIds);
        return userIds;
    }

    private TenantResp toResp(SysTenant tenant) {
        TenantResp resp = new TenantResp();
        BeanUtils.copyProperties(tenant, resp);
        return resp;
    }
}
