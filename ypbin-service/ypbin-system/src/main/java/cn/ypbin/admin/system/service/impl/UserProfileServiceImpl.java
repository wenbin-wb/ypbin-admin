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
        Long userId = IdentityContext.getUserId().orElse(null);
        String phone = accountSupport.normalizePhone(req.getPhone());
        accountSupport.checkPhoneUnique(phone, userId);
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "phone");
        user.setId(userId);
        boolean updated = userMapper.update(user, new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, userId)
            .set(SysUser::getPhone, phone)) > 0;
        if (!updated) {
            throw new BusinessException("个人资料更新失败");
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
        userMapper.updateById(update);
        accountSupport.recordPasswordHistory(userId, encoded);
    }
}
