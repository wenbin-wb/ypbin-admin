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

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserPasswordHistory;
import cn.ypbin.admin.system.entity.SysUserPost;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.mapper.SysUserPasswordHistoryMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.req.ChangePasswordReq;
import cn.ypbin.admin.system.model.req.ProfileUpdateReq;
import cn.ypbin.admin.system.model.req.UserSaveReq;
import cn.ypbin.admin.system.model.resp.ProfileResp;
import cn.ypbin.admin.system.model.resp.UserResp;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.security.password.policy.PasswordCheckResult;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends BaseServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysUserPasswordHistoryMapper passwordHistoryMapper;
    private final SysConfigService configService;
    private final PasswordValidator passwordValidator;

    @Override
    public SysUser getByUsername(String username) {
        // 用户名全局唯一，登录时尚无租户上下文，忽略租户过滤按用户名定位
        return TenantContext.executeIgnore(() ->
            getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username), false));
    }

    @Override
    public SysUser getByPhone(String phone) {
        return TenantContext.executeIgnore(() ->
            getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone), false));
    }

    @Override
    public void updateLastLoginTime(Long userId) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        updateById(update);
    }

    @Override
    @DataPermission
    public PageResult<UserResp> pageUsers(UserQuery query) {
        PageResult<SysUser> source = page(query, new LambdaQueryWrapper<SysUser>()
            .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
            .like(StringUtils.hasText(query.getRealName()), SysUser::getRealName, query.getRealName())
            .like(StringUtils.hasText(query.getPhone()), SysUser::getPhone, query.getPhone())
            .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
            .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
            .orderByDesc(SysUser::getCreateTime));
        List<UserResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    public UserResp getUserDetail(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserResp resp = toResp(user);
        resp.setRoleIds(userRoleMapper.selectRoleIdsByUserId(id));
        resp.setPostIds(userPostMapper.selectPostIdsByUserId(id));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserSaveReq req) {
        checkUsernameUnique(req.getUsername(), null);
        if (!StringUtils.hasText(req.getPassword())) {
            throw new BusinessException("新增用户必须设置密码");
        }
        validatePassword(req.getPassword(), req.getUsername());
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "roleIds", "password");
        String encoded = PasswordEncoderUtil.encode(req.getPassword());
        user.setPassword(encoded);
        user.setPwdResetTime(LocalDateTime.now());
        save(user);
        recordPasswordHistory(user.getId(), encoded);
        assignRolesInternal(user.getId(), req.getRoleIds());
        assignPosts(user.getId(), req.getPostIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserSaveReq req) {
        SysUser existing = getById(id);
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }
        checkUsernameUnique(req.getUsername(), id);
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "roleIds", "password");
        user.setId(id);
        // 密码留空表示不修改
        String encoded = null;
        if (StringUtils.hasText(req.getPassword())) {
            validatePassword(req.getPassword(), req.getUsername());
            encoded = PasswordEncoderUtil.encode(req.getPassword());
            user.setPassword(encoded);
            user.setPwdResetTime(LocalDateTime.now());
        }
        updateById(user);
        if (encoded != null) {
            recordPasswordHistory(id, encoded);
        }
        // 仅当显式传入 roleIds 时才重分配角色（null=不改动角色，空列表=清空角色）
        if (req.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
            assignRolesInternal(id, req.getRoleIds());
        }
        if (req.getPostIds() != null) {
            userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, id));
            assignPosts(id, req.getPostIds());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        if (id == 1L) {
            throw new BusinessException("内置超级管理员不可删除");
        }
        removeById(id);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String password) {
        SysUser user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (id == 1L) {
            throw new BusinessException("内置超级管理员不可重置密码");
        }
        if (!StringUtils.hasText(password)) {
            throw new BusinessException("新密码不能为空");
        }
        validatePassword(password, user.getUsername());
        checkPasswordHistory(id, password);
        String encoded = PasswordEncoderUtil.encode(password);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(encoded);
        update.setPwdResetTime(LocalDateTime.now());
        updateById(update);
        recordPasswordHistory(id, encoded);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        if (getById(id) == null) {
            throw new BusinessException("用户不存在");
        }
        // 覆盖式重设角色
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        assignRolesInternal(id, roleIds);
    }

    @Override
    public ProfileResp getProfile() {
        Long userId = LoginHelper.getUserId();
        SysUser user = getById(userId);
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
    public void updateProfile(ProfileUpdateReq req) {
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user);
        user.setId(LoginHelper.getUserId());
        updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordReq req) {
        Long userId = LoginHelper.getUserId();
        SysUser user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordEncoderUtil.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        validatePassword(req.getNewPassword(), user.getUsername());
        checkPasswordHistory(userId, req.getNewPassword());

        String encoded = PasswordEncoderUtil.encode(req.getNewPassword());
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(encoded);
        update.setPwdResetTime(LocalDateTime.now());
        updateById(update);
        recordPasswordHistory(userId, encoded);
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username)
            .ne(excludeId != null, SysUser::getId, excludeId));
        if (exists) {
            throw new BusinessException("用户名已存在：" + username);
        }
    }

    private void assignRolesInternal(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            userRoleMapper.insert(new SysUserRole(userId, roleId));
        }
    }

    private void assignPosts(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (Long postId : postIds) {
            userPostMapper.insert(new SysUserPost(userId, postId));
        }
    }

    /**
     * 按密码策略校验密码复杂度，不通过抛业务异常。
     */
    private void validatePassword(String rawPassword, String username) {
        PasswordCheckResult result = passwordValidator.check(rawPassword, username);
        if (!result.passed()) {
            throw new BusinessException(result.message());
        }
    }

    /**
     * 校验新密码是否与最近 N 次历史密码重复（N 由 PASSWORD_HISTORY_COUNT 控制，0 不校验）。
     */
    private void checkPasswordHistory(Long userId, String rawPassword) {
        int historyCount = configService.getInt("PASSWORD_HISTORY_COUNT", 0);
        if (historyCount <= 0) {
            return;
        }
        List<SysUserPasswordHistory> histories = passwordHistoryMapper.selectList(
            new LambdaQueryWrapper<SysUserPasswordHistory>()
                .eq(SysUserPasswordHistory::getUserId, userId)
                .orderByDesc(SysUserPasswordHistory::getCreateTime)
                .last("LIMIT " + historyCount));
        boolean reused = histories.stream()
            .anyMatch(h -> PasswordEncoderUtil.matches(rawPassword, h.getPassword()));
        if (reused) {
            throw new BusinessException("新密码不能与最近 " + historyCount + " 次使用过的密码相同");
        }
    }

    /**
     * 记录一条历史密码。
     */
    private void recordPasswordHistory(Long userId, String encodedPassword) {
        SysUserPasswordHistory history = new SysUserPasswordHistory();
        history.setUserId(userId);
        history.setPassword(encodedPassword);
        passwordHistoryMapper.insert(history);
    }

    private UserResp toResp(SysUser user) {
        UserResp resp = new UserResp();
        BeanUtils.copyProperties(user, resp);
        return resp;
    }
}
