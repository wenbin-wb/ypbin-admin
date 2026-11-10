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
import cn.ypbin.admin.common.constant.PwdChangeKeys;
import cn.ypbin.admin.common.constant.RegisterConfigKeys;
import cn.ypbin.admin.modules.system.entity.SysPost;
import cn.ypbin.admin.modules.system.entity.SysRole;
import cn.ypbin.admin.modules.system.entity.SysRoleMenu;
import cn.ypbin.admin.modules.system.entity.SysTenant;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.entity.SysUserPost;
import cn.ypbin.admin.modules.system.entity.SysUserRole;
import cn.ypbin.admin.modules.system.enums.UserStatusEnum;
import cn.ypbin.admin.modules.system.mapper.SysPostMapper;
import cn.ypbin.admin.modules.system.mapper.SysRoleMapper;
import cn.ypbin.admin.modules.system.mapper.SysRoleMenuMapper;
import cn.ypbin.admin.modules.system.mapper.SysTenantMapper;
import cn.ypbin.admin.modules.system.mapper.SysUserMapper;
import cn.ypbin.admin.modules.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.modules.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.modules.system.model.query.OnlineUserQuery;
import cn.ypbin.admin.modules.system.model.query.UserQuery;
import cn.ypbin.admin.modules.system.model.req.UserRegisterReq;
import cn.ypbin.admin.modules.system.model.req.UserSaveReq;
import cn.ypbin.admin.modules.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.modules.system.model.resp.UserResp;
import cn.ypbin.admin.modules.system.model.vo.UserImportResult;
import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.admin.modules.system.service.SysSpaceRelationService;
import cn.ypbin.admin.modules.system.service.SysUserService;
import cn.ypbin.admin.modules.system.service.support.OnlineTokenFingerprint;
import cn.ypbin.admin.modules.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.online.OnlineUser;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
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
import org.springframework.dao.DuplicateKeyException;
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

    /** 空间关联的目标类型：用户。 */
    private static final String SPACE_TARGET_USER = "user";

    private final SysUserRoleMapper userRoleMapper;
    private final SysUserPostMapper userPostMapper;
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
    private final OnlineUserService onlineUserService;
    private final UserExcelComponent userExcelComponent;
    private final UserAccountSupport accountSupport;
    /** 用户删除时清理空间关联，避免留下指向已删用户的孤儿关联。 */
    private final SysSpaceRelationService spaceRelationService;

    /** 系统参数（注册开关/默认租户/默认角色）读取 */
    private final SysConfigService configService;

    /** 租户存在性与启用状态校验（匿名注册没有登录身份，租户只能由系统参数指定） */
    private final SysTenantMapper tenantMapper;

    /** 默认角色的菜单授权校验（无授权的角色登录后是空系统，必须拒绝而不是静默放行） */
    private final SysRoleMenuMapper roleMenuMapper;

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
        // 只 set 目标列：new SysUser() + updateById 会连带写入 BaseEntity 的 status 默认值
        update(new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, userId)
            .set(SysUser::getLastLoginTime, LocalDateTime.now()));
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

    /**
     * 用户自助注册。
     *
     * <p><b>为什么必须显式绑定租户作用域</b>：匿名注册链路没有登录身份（{@code UserContext} 为空），
     * 而 {@code sys_user} 不在租户忽略表内（fail-closed），直接读写会抛「缺少租户上下文」。
     * 归属租户只取系统参数 {@code REGISTER_DEFAULT_TENANT_ID} 并校验其存在且启用——
     * 不接受调用方指定租户，否则任何人都能对任意租户自助开户。</p>
     *
     * <p><b>默认角色是硬约束，不是可选项</b>：注册用户若没有角色则登录后看不到任何菜单，
     * 表现为「注册成功但系统空白」。故 {@code REGISTER_DEFAULT_ROLE_CODE} 未配置、角色不存在、
     * 已停用或不属于该租户时一律拒绝注册，不静默创建无角色用户。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser registerUser(UserRegisterReq req) {
        if (!configService.getBoolean(RegisterConfigKeys.REGISTER_ENABLED, false)) {
            throw new BusinessException("注册功能未开启");
        }
        Long tenantId = resolveRegisterTenantId();
        // 角色查询受租户插件约束，必须与用户写入处在同一个显式租户作用域
        return TenantContext.executeWithTenant(tenantId, () -> {
            SysRole role = resolveRegisterRole(tenantId);
            return createRegisteredUser(req, tenantId, role);
        });
    }

    /**
     * 解析注册归属租户：取系统参数并校验租户存在且启用。
     *
     * @return 租户 ID
     */
    private Long resolveRegisterTenantId() {
        String raw = configService.getString(RegisterConfigKeys.REGISTER_DEFAULT_TENANT_ID, "");
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException("注册未配置默认租户，请联系管理员");
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("系统参数 " + RegisterConfigKeys.REGISTER_DEFAULT_TENANT_ID
                + " 必须为整数");
        }
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || !EntityStatus.ENABLED.getCode().equals(tenant.getStatus())) {
            throw new BusinessException("注册默认租户不存在或已停用");
        }
        return tenantId;
    }

    /**
     * 解析注册默认角色：必须是该租户下启用的租户角色。
     *
     * @param tenantId 归属租户 ID（调用方已进入该租户作用域）
     * @return 默认角色
     */
    private SysRole resolveRegisterRole(Long tenantId) {
        String roleCode = configService.getString(RegisterConfigKeys.REGISTER_DEFAULT_ROLE_CODE, "");
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException("注册未配置默认角色，请联系管理员");
        }
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
            .eq(SysRole::getCode, roleCode.trim())
            .eq(SysRole::getRoleType, AdminConstants.ROLE_TYPE_TENANT)
            .eq(SysRole::getStatus, EntityStatus.ENABLED.getCode()), false);
        if (role == null || !tenantId.equals(role.getTenantId())) {
            throw new BusinessException(
                "注册默认角色不存在、已停用或不属于默认租户：" + roleCode.trim());
        }
        // 角色存在≠可用：权限码/路由来自「角色的菜单授权 ∩ 租户模板」，没有菜单授权的角色
        // 登录后是空系统。必须在注册前拒绝，否则问题要等用户登录才暴露，且没有任何报错。
        long menuCount = roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
            .eq(SysRoleMenu::getRoleId, role.getId()));
        if (menuCount == 0) {
            throw new BusinessException(
                "注册默认角色未分配菜单权限，请联系管理员为角色授权：" + roleCode.trim());
        }
        return role;
    }

    /**
     * 落库一个新注册用户（用户名/手机号查重、密码策略、密码历史、默认角色）。
     *
     * @param req      注册请求
     * @param tenantId 归属租户 ID
     * @param role     默认角色
     * @return 新建用户
     */
    private SysUser createRegisteredUser(UserRegisterReq req, Long tenantId, SysRole role) {
        String username = req.getUsername().trim();
        String realName = req.getRealName().trim();
        String phone = accountSupport.normalizePhone(req.getPhone());
        // 用户名是跨租户全局唯一键：必须忽略租户过滤查重，否则跨租户重名会落到数据库唯一键报原始 SQL 错误
        boolean usernameExists = TenantContext.executeIgnore(() -> exists(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)));
        if (usernameExists) {
            throw new BusinessException("用户名已存在：" + username);
        }
        accountSupport.checkPhoneUnique(phone, null);
        accountSupport.validatePassword(req.getPassword(), username);

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setRealName(realName);
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname().trim() : realName);
        user.setPhone(phone);
        user.setEmail(StringUtils.hasText(req.getEmail()) ? req.getEmail().trim() : null);
        user.setUserType(AdminConstants.USER_TYPE_TENANT);
        user.setTenantId(tenantId);
        String encoded = PasswordEncoderUtil.encode(req.getPassword());
        user.setPassword(encoded);
        user.setPwdResetTime(LocalDateTime.now());
        user.setStatus(EntityStatus.ENABLED.getCode());
        try {
            if (!save(user)) {
                throw new BusinessException("注册失败，请稍后重试");
            }
        } catch (DuplicateKeyException e) {
            // 并发注册或「逻辑删除后用户名仍占用唯一键」都可能走到这里：
            // 数据库原始异常不能透给匿名调用方，转成与查重一致的可读提示
            throw new BusinessException("用户名或手机号已存在，请更换后重试");
        }
        accountSupport.recordPasswordHistory(user.getId(), encoded);
        assignRolesInternal(user.getId(), List.of(role.getId()));
        return user;
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
        // status 不随编辑表单更新：它必须走 updateStatus（有「禁止禁用自己」与强制下线保护），
        // 否则一次普通编辑就能绕过这两道守卫
        BeanUtils.copyProperties(req, user, "roleIds", "postIds", "password", "phone", "status");
        user.setId(id);
        // 密码留空表示不修改
        String encoded = null;
        if (StringUtils.hasText(req.getPassword())) {
            accountSupport.validatePassword(req.getPassword(), req.getUsername());
            encoded = PasswordEncoderUtil.encode(req.getPassword());
            user.setPassword(encoded);
            user.setPwdResetTime(LocalDateTime.now());
        }
        // 手机号区分「未提交（null，保持不变）」与「显式清空（空串）」：
        // wrapper 的 set 不受 MyBatis-Plus「null 不更新」保护
        boolean updated = update(user, new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .set(req.getPhone() != null, SysUser::getPhone, phone));
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
        if (id.equals(LoginHelper.getUserId()) && UserStatusEnum.DISABLED.getCode().equals(status)) {
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
        // 空间是多态关联：用户删除后清理其空间归属，避免关联表指向已删用户
        spaceRelationService.removeByTarget(SPACE_TARGET_USER, id);
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
        // 只 set 目标列：new SysUser() + updateById 会带上 BaseEntity 的 status 默认值 1，
        // 导致「重置已禁用用户密码」把账号静默重新启用
        update(new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .set(SysUser::getPassword, encoded)
            .set(SysUser::getPwdResetTime, LocalDateTime.now())
            // 管理员重置的口令也是一次性初始口令：下次登录必须先自行改密
            .set(SysUser::getPwdChangeRequired, PwdChangeKeys.REQUIRED_YES));
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
    public PageResult<OnlineUserResp> pageOnlineUsers(OnlineUserQuery query) {
        long page = query.getPage();
        long pageSize = query.getPageSize();
        if (page < 1 || pageSize < 1) {
            throw new BusinessException("页码与每页条数必须大于 0");
        }
        List<OnlineUser> users = StringUtils.hasText(query.getKeyword())
            ? onlineUserService.list(query.getKeyword())
            : onlineUserService.list();
        long total = users.size();
        // 先算页边界再取数据：越界页直接返回空列表，不做任何多余的会话补充查询
        long fromIndex = (page - 1) * pageSize;
        List<OnlineUser> pageUsers = fromIndex >= total ? List.of()
            : users.subList((int) fromIndex, (int) Math.min(fromIndex + pageSize, total));
        if (pageUsers.isEmpty()) {
            return PageResult.of(List.of(), total, page, pageSize);
        }
        List<Long> ids = pageUsers.stream()
            .map(OnlineUser::getUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        // 姓名回填必须忽略租户过滤：在线会话本身是跨租户的（本接口挂在 @PlatformAccess 下），
        // 否则只有本租户用户能回填到姓名，他租户行的 realName 静默为 null
        Map<Long, String> realNameById = ids.isEmpty() ? Map.of()
            : TenantContext.executeIgnore(() -> listByIds(ids)).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        List<OnlineUserResp> items = pageUsers.stream().map(u -> {
            OnlineUserResp r = new OnlineUserResp();
            BeanUtils.copyProperties(u, r, "token");
            r.setRealName(realNameById.get(u.getUserId()));
            // 只回传不可逆指纹：token 原文是可复用的 Bearer 凭据，不能出现在列表响应里
            r.setTokenHash(OnlineTokenFingerprint.hash(u.getToken()));
            return r;
        }).toList();
        return PageResult.of(items, total, page, pageSize);
    }

    @Override
    public void kickoutByTokenHash(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            throw new BusinessException("会话标识不能为空");
        }
        // 会话存储没有「按指纹查」的接口，只能枚举当前在线会话后逐个比对（低频管理动作）
        for (OnlineUser onlineUser : onlineUserService.list()) {
            String currentHash = OnlineTokenFingerprint.hash(onlineUser.getToken());
            if (currentHash != null && currentHash.equalsIgnoreCase(tokenHash.trim())) {
                onlineUserService.kickoutByToken(onlineUser.getToken());
                return;
            }
        }
        throw new BusinessException("会话不存在或已失效");
    }
}
