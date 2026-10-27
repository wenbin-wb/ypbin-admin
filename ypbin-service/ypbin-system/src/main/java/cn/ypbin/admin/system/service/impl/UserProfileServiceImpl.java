/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.model.req.ChangePasswordReq;
import cn.ypbin.admin.system.model.req.ProfileUpdateReq;
import cn.ypbin.admin.system.model.resp.ProfileResp;
import cn.ypbin.admin.system.service.UserProfileService;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.security.online.OnlineUserService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人中心服务实现。操作对象恒为当前登录用户。
 *
 * @author wenbin
 * @since 2026-08-31
 */
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final UserAccountSupport accountSupport;
    private final OnlineUserService onlineUserService;

    @Override
    public ProfileResp getProfile() {
        Long userId = IdentityContext.getUserId().orElse(null);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 本人查看本人：手机/邮箱不脱敏，供编辑表单原样回填
        ProfileResp resp = new ProfileResp();
        BeanUtils.copyProperties(user, resp);
        resp.setRoleIds(userRoleMapper.selectRoleIdsByUserId(userId));
        resp.setPostIds(userPostMapper.selectPostIdsByUserId(userId));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileUpdateReq req) {
        Long userId = IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
        SysUser existing = userMapper.selectById(userId);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        // null = 未提交该字段（保持不变）；空串 = 显式清空。
        // 此前无条件 set(phone, null) 会在「只改昵称」的局部提交里把手机号清掉。
        boolean phoneProvided = req.getPhone() != null;
        String phone = phoneProvided ? accountSupport.normalizePhone(req.getPhone()) : null;
        if (phoneProvided) {
            accountSupport.checkPhoneUnique(phone, userId);
        }
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "phone");
        user.setId(userId);
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, userId);
        if (phoneProvided) {
            wrapper.set(SysUser::getPhone, phone);
        }
        if (userMapper.update(user, wrapper) == 0) {
            throw new BusinessException("个人资料更新失败");
        }
        // SysCache 是永久缓存：个人中心自更新不会经过管理端 updateUser，必须在这里主动失效，
        // 否则重新登录仍读到旧的姓名/头像/手机号快照
        SysCache.evictUser(userId, existing.getUsername());
        SysCache.evictUserByPhone(existing.getPhone());
        if (phoneProvided) {
            SysCache.evictUserByPhone(phone);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordReq req) {
        Long userId = IdentityContext.getUserId().orElse(null);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordEncoderUtil.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        accountSupport.validatePassword(req.getNewPassword(), user.getUsername());
        accountSupport.checkPasswordHistory(userId, req.getNewPassword());

        String encoded = PasswordEncoderUtil.encode(req.getNewPassword());
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(encoded);
        update.setPwdResetTime(LocalDateTime.now());
        if (userMapper.updateById(update) != 1) {
            throw new BusinessException("密码修改失败");
        }
        accountSupport.recordPasswordHistory(userId, encoded);
        // 改密后与管理员重置口令同口径：清快照缓存并强制下线既有会话
        SysCache.evictUser(userId, user.getUsername());
        SysCache.evictUserByPhone(user.getPhone());
        onlineUserService.kickoutByUserId(userId);
    }
}
