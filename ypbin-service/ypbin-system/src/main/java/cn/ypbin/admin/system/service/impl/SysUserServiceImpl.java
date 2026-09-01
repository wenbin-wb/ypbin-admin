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

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.system.entity.SysPost;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserPost;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.req.UserSaveReq;
import cn.ypbin.admin.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.system.model.resp.UserResp;
import cn.ypbin.admin.system.model.vo.UserImportResult;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.online.OnlineUser;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

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
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
    private final OnlineUserService onlineUserService;
    private final UserExcelComponent userExcelComponent;
    private final UserAccountSupport accountSupport;

    @Override
    public SysUser getByUsername(String username) {
        // 用户名全局唯一，登录时尚无租户上下文，忽略租户过滤按用户名定位
        return TenantContext.executeIgnore(() ->
            getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username), false));
    }

    @Override
    public SysUser getByPhone(String phone) {
        String normalizedPhone = accountSupport.normalizePhone(phone);
        if (normalizedPhone == null) {
            throw new BusinessException("手机号不能为空");
        }
        return TenantContext.executeIgnore(() ->
            getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, normalizedPhone), true));
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
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_TENANT)
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
    @DataPermission
    public UserResp getUserDetail(Long id) {
        SysUser user = getManageableUser(id);
        UserResp resp = toResp(user);
        resp.setRoleIds(userRoleMapper.selectRoleIdsByUserId(id));
        resp.setPostIds(userPostMapper.selectPostIdsByUserId(id));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserSaveReq req) {
        checkUsernameUnique(req.getUsername(), null);
        String phone = accountSupport.normalizePhone(req.getPhone());
        accountSupport.checkPhoneUnique(phone, null);
        if (!StringUtils.hasText(req.getPassword())) {
            throw new BusinessException("新增用户必须设置密码");
        }
        accountSupport.validatePassword(req.getPassword(), req.getUsername());
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "roleIds", "postIds", "password", "phone");
        user.setPhone(phone);
        user.setUserType(AdminConstants.USER_TYPE_TENANT);
        user.setTenantId(UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法确定当前租户")));
        String encoded = PasswordEncoderUtil.encode(req.getPassword());
        user.setPassword(encoded);
        user.setPwdResetTime(LocalDateTime.now());
        validateAssignments(user, req.getRoleIds(), req.getPostIds());
        save(user);
        accountSupport.recordPasswordHistory(user.getId(), encoded);
        assignRolesInternal(user.getId(), req.getRoleIds());
        assignPosts(user.getId(), req.getPostIds());
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserSaveReq req) {
        SysUser existing = getManageableUser(id);
        checkUsernameUnique(req.getUsername(), id);
        String phone = accountSupport.normalizePhone(req.getPhone());
        accountSupport.checkPhoneUnique(phone, id);
        validateAssignments(existing, req.getRoleIds(), req.getPostIds());
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "roleIds", "postIds", "password", "phone");
        user.setId(id);
        // 密码留空表示不修改
        String encoded = null;
        if (StringUtils.hasText(req.getPassword())) {
            accountSupport.validatePassword(req.getPassword(), req.getUsername());
            encoded = PasswordEncoderUtil.encode(req.getPassword());
            user.setPassword(encoded);
            user.setPwdResetTime(LocalDateTime.now());
        }
        boolean updated = update(user, new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .set(SysUser::getPhone, phone));
        if (!updated) {
            throw new BusinessException("用户更新失败");
        }
        if (encoded != null) {
            accountSupport.recordPasswordHistory(id, encoded);
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
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser user = getManageableUser(id);
        if (id.equals(IdentityContext.getUserId().orElse(null)) && UserStatusEnum.DISABLED.getCode().equals(status)) {
            throw new BusinessException("不允许禁用当前用户");
        }
        boolean updated = update(new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, user.getId())
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_TENANT)
            .set(SysUser::getStatus, status));
        if (!updated) {
            throw new BusinessException("用户状态更新失败");
        }
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        getManageableUser(id);
        boolean phoneCleared = update(new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .set(SysUser::getPhone, null));
        if (!phoneCleared || !removeById(id)) {
            throw new BusinessException("用户删除失败");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, id));
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String password) {
        SysUser user = getManageableUser(id);
        if (!StringUtils.hasText(password)) {
            throw new BusinessException("新密码不能为空");
        }
        accountSupport.validatePassword(password, user.getUsername());
        accountSupport.checkPasswordHistory(id, password);
        String encoded = PasswordEncoderUtil.encode(password);
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(encoded);
        update.setPwdResetTime(LocalDateTime.now());
        updateById(update);
        accountSupport.recordPasswordHistory(id, encoded);
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = getManageableUser(id);
        validateAssignments(user, roleIds, null);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        assignRolesInternal(id, roleIds);
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username)
            .ne(excludeId != null, SysUser::getId, excludeId));
        if (exists) {
            throw new BusinessException("用户名已存在：" + username);
        }
    }

    private void validateAssignments(SysUser user, List<Long> roleIds, List<Long> postIds) {
        validateRoles(user, roleIds);
        validatePosts(user, postIds);
    }

    private void validateRoles(SysUser user, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> requestedIds = new HashSet<>(roleIds);
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
            .in(SysRole::getId, requestedIds)
            .eq(SysRole::getStatus, EntityStatus.ENABLED.getCode()));
        Set<Long> existingIds = roles.stream().map(SysRole::getId).collect(Collectors.toSet());
        boolean invalid = !existingIds.equals(requestedIds) || roles.stream().anyMatch(role ->
            !user.getTenantId().equals(role.getTenantId())
                || !AdminConstants.ROLE_TYPE_TENANT.equals(role.getRoleType()));
        if (invalid) {
            throw new BusinessException("用户角色包含不存在、已禁用、跨租户或平台角色");
        }
    }

    private void validatePosts(SysUser user, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        Set<Long> requestedIds = new HashSet<>(postIds);
        List<SysPost> posts = postMapper.selectList(new LambdaQueryWrapper<SysPost>()
            .in(SysPost::getId, requestedIds)
            .eq(SysPost::getStatus, EntityStatus.ENABLED.getCode()));
        Set<Long> existingIds = posts.stream().map(SysPost::getId).collect(Collectors.toSet());
        boolean invalid = !existingIds.equals(requestedIds)
            || posts.stream().anyMatch(post -> !user.getTenantId().equals(post.getTenantId()));
        if (invalid) {
            throw new BusinessException("用户岗位包含不存在、已禁用或跨租户岗位");
        }
    }

    private SysUser getManageableUser(Long id) {
        SysUser user = getOne(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_TENANT), false);
        if (user == null) {
            throw new BusinessException("用户不存在或无权操作");
        }
        return user;
    }

    private void assignRolesInternal(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : new HashSet<>(roleIds)) {
            userRoleMapper.insert(new SysUserRole(userId, roleId));
        }
    }

    private void assignPosts(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        for (Long postId : new HashSet<>(postIds)) {
            userPostMapper.insert(new SysUserPost(userId, postId));
        }
    }

    private UserResp toResp(SysUser user) {
        UserResp resp = new UserResp();
        BeanUtils.copyProperties(user, resp);
        return resp;
    }

    @Override
    @DataPermission
    public void exportUsers(UserQuery query, HttpServletResponse response) {
        userExcelComponent.exportUsers(query, response);
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        userExcelComponent.downloadImportTemplate(response);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResult importUsers(MultipartFile file) {
        return userExcelComponent.importUsers(file);
    }

    @Override
    public List<OnlineUserResp> listOnlineUsers(String keyword) {
        List<OnlineUser> users = StringUtils.hasText(keyword)
            ? onlineUserService.list(keyword)
            : onlineUserService.list();
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> ids = users.stream().map(OnlineUser::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> realNameById = ids.isEmpty() ? Map.of()
            : listByIds(ids).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        return users.stream().map(u -> {
            OnlineUserResp r = new OnlineUserResp();
            BeanUtils.copyProperties(u, r);
            r.setRealName(realNameById.get(u.getUserId()));
            return r;
        }).toList();
    }
}
