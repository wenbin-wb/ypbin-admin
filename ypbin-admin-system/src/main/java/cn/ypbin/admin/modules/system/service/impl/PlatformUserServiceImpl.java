/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.enums.UserStatusEnum;
import cn.ypbin.admin.modules.system.mapper.SysUserMapper;
import cn.ypbin.admin.modules.system.model.query.PlatformUserQuery;
import cn.ypbin.admin.modules.system.model.resp.PlatformUserResp;
import cn.ypbin.admin.modules.system.service.PlatformUserService;
import cn.ypbin.admin.modules.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 平台账号服务实现。
 *
 * @author wenbin
 * @since 2026-11-17
 */
@Service
@RequiredArgsConstructor
public class PlatformUserServiceImpl extends BaseServiceImpl<SysUserMapper, SysUser>
    implements PlatformUserService {

    private final UserAccountSupport accountSupport;

    @Override
    public PageResult<PlatformUserResp> pagePlatformUsers(PlatformUserQuery query) {
        // 平台账号不属于任何租户业务数据，这里固定按 user_type 过滤，不暴露给租户查询条件
        PageResult<SysUser> source = page(query, new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_PLATFORM)
            .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
            .like(StringUtils.hasText(query.getRealName()), SysUser::getRealName, query.getRealName())
            .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
            .orderByAsc(SysUser::getCreateTime));
        List<PlatformUserResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser user = requirePlatformUser(id);
        if (user.getId().equals(LoginHelper.getUserId())
            && UserStatusEnum.DISABLED.getCode().equals(status)) {
            throw new BusinessException("不允许禁用当前用户");
        }
        boolean updated = update(new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_PLATFORM)
            .set(SysUser::getStatus, status));
        if (!updated) {
            throw new BusinessException("平台账号状态更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String password) {
        SysUser user = requirePlatformUser(id);
        accountSupport.validatePassword(password, user.getUsername());
        accountSupport.checkPasswordHistory(id, password);
        String encoded = PasswordEncoderUtil.encode(password);
        boolean updated = update(new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_PLATFORM)
            .set(SysUser::getPassword, encoded)
            .set(SysUser::getPwdResetTime, LocalDateTime.now()));
        if (!updated) {
            throw new BusinessException("平台账号密码重置失败");
        }
        accountSupport.recordPasswordHistory(id, encoded);
    }

    private SysUser requirePlatformUser(Long id) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_PLATFORM), false);
        if (user == null) {
            throw new BusinessException("平台账号不存在");
        }
        return user;
    }

    private PlatformUserResp toResp(SysUser user) {
        PlatformUserResp resp = new PlatformUserResp();
        BeanUtils.copyProperties(user, resp);
        return resp;
    }
}
