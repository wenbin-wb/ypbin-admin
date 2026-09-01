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

import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.system.service.SocialBindService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户-第三方平台绑定服务实现。
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Service
@RequiredArgsConstructor
public class SocialBindServiceImpl implements SocialBindService {

    private final SysUserSocialMapper socialMapper;

    @Override
    public SysUserSocial getByPlatformAndOpenId(String platform, String openId) {
        return socialMapper.selectOne(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getPlatform, platform)
            .eq(SysUserSocial::getOpenId, openId), false);
    }

    @Override
    public boolean isUserBound(Long userId, String platform) {
        return socialMapper.exists(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, userId)
            .eq(SysUserSocial::getPlatform, platform));
    }

    @Override
    public boolean isAccountBound(String platform, String openId) {
        return socialMapper.exists(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getPlatform, platform)
            .eq(SysUserSocial::getOpenId, openId));
    }

    @Override
    public void save(SysUserSocial social) {
        socialMapper.insert(social);
    }

    @Override
    public void unbind(Long userId, String platform) {
        socialMapper.delete(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, userId)
            .eq(SysUserSocial::getPlatform, platform));
    }

    @Override
    public List<SysUserSocial> listByUserId(Long userId) {
        return socialMapper.selectList(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, userId));
    }
}
