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
import cn.ypbin.admin.system.api.cache.SysCache;
import cn.ypbin.admin.system.entity.SysPost;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserPost;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.mapper.SysUserPostMapper;
import cn.ypbin.admin.system.mapper.SysUserRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserSocialMapper;
import cn.ypbin.admin.system.model.query.OnlineUserQuery;
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.req.UserSaveReq;
import cn.ypbin.admin.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.system.model.resp.UserResp;
import cn.ypbin.admin.system.model.vo.UserImportResult;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.admin.system.service.support.DataScopeResolver;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.cache.annotation.CacheEvict;
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
    private final SysUserSocialMapper userSocialMapper;
    private final SysRoleMapper roleMapper;
    private final SysPostMapper postMapper;
    private final OnlineUserService onlineUserService;
    private final UserExcelComponent userExcelComponent;
    private final UserAccountSupport accountSupport;
    /**
     * 数据范围解析能力（{@code service/support} 层的业务接口，非 starter 端口的实现类）。
     *
     * <p>写路径必须显式校验 {@code deptId}，而「部门是否在范围内」的口径只能有一份实现，
     * 故复用与读路径同一个解析器，而不是在服务里重写一遍超管判定 + 角色/部门树解析；
     * 这里只依赖能力接口，不依赖 {@code provider} 包中的端口适配器。</p>
     */
    private final DataScopeResolver dataScopeResolver;

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
    public SysUser getByIdGlobal(Long userId) {
        // 内部端点（登录/第三方回调/令牌校验）到达这里时通常还没有网关签发的租户头，
        // 租户拦截器 fail-closed 会抛「缺少租户上下文」，故显式忽略租户过滤——
        // 与 getByUsername/getByPhone 同口径，且不像覆写 getById 那样改变其它调用者的隔离语义
        return TenantContext.executeIgnore(() -> getById(userId));
    }

    @Override
    public boolean verifyPassword(Long userId, String rawPassword) {
        SysUser user = TenantContext.executeIgnore(() -> getById(userId));
        return user != null && user.getPassword() != null
            && PasswordEncoderUtil.matches(rawPassword, user.getPassword());
    }

    @Override
    public long countUsers() {
        return TenantContext.executeIgnore(this::count);
    }

    @Override
    public List<SysUser> searchUsers(String keyword) {
        return TenantContext.executeIgnore(() -> list(new LambdaQueryWrapper<SysUser>()
            .like(SysUser::getUsername, keyword)
            .or()
            .like(SysUser::getRealName, keyword)
            .last("LIMIT 10")));
    }

    @Override
    public void updateLastLoginTime(Long userId) {
        SysUser update = new SysUser();
        update.setId(userId);
        update.setLastLoginTime(LocalDateTime.now());
        // 登录成功回写时通常尚无租户上下文（auth 经 Feign 调用），须忽略租户行级过滤
        TenantContext.executeIgnore(() -> {
            updateById(update);
            return null;
        });
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
    @CacheEvict(keys = {"'sys:user:v2:username:' + #req.username"})
    public void createUser(UserSaveReq req) {
        // 先鉴权再校验/落库：数据范围是授权判断，fail-fast 可避免为越权请求白跑查重与密码策略
        validateDeptInScope(req.getDeptId());
        accountSupport.checkUsernameUnique(req.getUsername(), null);
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
        // 防幽灵：同手机号的已删历史用户若残留旧缓存，不清则新用户可能读到旧快照
        SysCache.evictUserByPhone(phone);
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(keys = {"'sys:user:v2:id:' + #id", "'sys:user:v2:username:' + #req.username"})
    public void updateUser(Long id, UserSaveReq req) {
        // 先鉴权再校验/落库：目标部门越界直接拒绝，不进入后续查重与更新
        validateDeptInScope(req.getDeptId());
        SysUser existing = getManageableUser(id);
        accountSupport.checkUsernameUnique(req.getUsername(), id);
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
            // 角色变更：清该用户权限缓存（权限码/角色码可能已变）
            SysCache.evictUserAuth(id);
        }
        if (req.getPostIds() != null) {
            userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, id));
            assignPosts(id, req.getPostIds());
        }
        // 清缓存：新 username 由 @CacheEvict 注解清；旧 username/旧 phone 是方法内局部变量，手动清（防旧 key 残留）
        SysCache.evictUser(null, existing.getUsername());
        SysCache.evictUserByPhone(existing.getPhone());
        SysCache.evictUserByPhone(phone);
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(keys = {"'sys:user:v2:id:' + #id"})
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
        // 清 username/phone 快照缓存（id 键由 @CacheEvict 清），避免启用/禁用后旧状态快照残留
        // 导致登录仍命中旧状态；禁用时强制下线既有会话
        SysCache.evictUser(user.getId(), user.getUsername());
        SysCache.evictUserByPhone(user.getPhone());
        if (UserStatusEnum.DISABLED.getCode().equals(status)) {
            onlineUserService.kickoutByUserId(user.getId());
        }
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(keys = {"'sys:user:v2:id:' + #id"})
    public void deleteUser(Long id) {
        SysUser existing = getManageableUser(id);
        // 删除前取回第三方绑定（手机号随后置空，旧号缓存须用删除前值清理）
        List<SysUserSocial> socials = userSocialMapper.selectList(
            new LambdaQueryWrapper<SysUserSocial>().eq(SysUserSocial::getUserId, id));
        boolean phoneCleared = update(new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, id)
            .set(SysUser::getPhone, null));
        if (!phoneCleared || !removeById(id)) {
            throw new BusinessException("用户删除失败");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userPostMapper.delete(new LambdaQueryWrapper<SysUserPost>().eq(SysUserPost::getUserId, id));
        // 清理第三方绑定行与绑定缓存（防 openId 永久占用、社交登录读到已删用户）
        userSocialMapper.delete(new LambdaQueryWrapper<SysUserSocial>()
            .eq(SysUserSocial::getUserId, id));
        for (SysUserSocial social : socials) {
            SysCache.evictSocialBinding(id, social.getPlatform(), social.getOpenId());
        }
        // 清理 username/phone/role/perm 永久缓存键并强制下线，防幽灵身份与旧角色残留
        SysCache.evictUser(existing.getId(), existing.getUsername());
        SysCache.evictUserByPhone(existing.getPhone());
        SysCache.evictUserAuth(id);
        onlineUserService.kickoutByUserId(id);
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(keys = {"'sys:user:v2:id:' + #id"})
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
        // 改密后清用户快照缓存并强制下线既有会话，要求重新登录（防旧口令会话长期有效）
        SysCache.evictUser(id, user.getUsername());
        SysCache.evictUserByPhone(user.getPhone());
        onlineUserService.kickoutByUserId(id);
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(keys = {"'sys:role:user:' + #id", "'sys:perm:user:' + #id"})
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = getManageableUser(id);
        validateAssignments(user, roleIds, null);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        assignRolesInternal(id, roleIds);
    }

    private void validateAssignments(SysUser user, List<Long> roleIds, List<Long> postIds) {
        validateRoles(user, roleIds);
        validatePosts(user, postIds);
    }

    /**
     * 校验目标部门落在当前操作者的数据范围内（写路径的显式校验）。
     *
     * <p><b>为什么不能靠 {@code @DataPermission} 解决</b>：它只把范围条件拼进被标注方法内的 SQL，
     * 而 {@code deptId} 是请求入参、不经过任何查询——{@code createUser} 甚至没有 {@code @DataPermission}。
     * 于是部门范围管理员可以把自己范围内的用户改到别的部门、或直接建到别的部门。
     * 这里调 {@code DataScopeResolver#isDeptWithinScope}（与读路径同一套口径）显式判定。</p>
     *
     * <p>越界、以及「无部门」（读路径 {@code dept_id IN (...)} 匹配不到 NULL 行）一律显式抛业务错误，
     * 不静默忽略、也不留给数据库层报错。校验过程中解析器自己的查询由其 {@code RESOLVING} 标记短路，
     * 不会再次叠加数据范围、也不会递归。</p>
     *
     * @param deptId 目标部门 ID（可为 {@code null}）
     */
    private void validateDeptInScope(Long deptId) {
        if (!dataScopeResolver.isDeptWithinScope(deptId)) {
            throw new BusinessException("目标部门不在你的数据范围内，无权在该部门下新建或调整用户");
        }
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
        // 先构建整批、再一次性批量写：逐条 insert 会让 N 个角色变成 N 次数据库往返
        List<SysUserRole> rows = new HashSet<>(roleIds).stream()
            .map(roleId -> new SysUserRole(userId, roleId))
            .toList();
        userRoleMapper.insertBatch(rows);
    }

    private void assignPosts(Long userId, List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return;
        }
        List<SysUserPost> rows = new HashSet<>(postIds).stream()
            .map(postId -> new SysUserPost(userId, postId))
            .toList();
        userPostMapper.insertBatch(rows);
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

    /**
     * 分页查询在线用户（<strong>内存分页</strong>）。
     *
     * <p>在线用户来自 sa-token 会话存储而非数据库：会话枚举接口只提供「按关键字全量列出」，
     * 没有可下推的 SQL 分页条件，因此这里先取全量再切片。代价是每页请求都要枚举并逐个读取全部
     * 在线会话（O(在线会话数) 次会话读取，与页码/每页条数无关）；分页收益只在传输量与渲染量。
     * 在线规模的量级是「当前登录会话数」，与该代价相称；若日后在线数达到万级，应改为在会话侧
     * 维护可分页索引，而不是继续放大这里的切片。</p>
     *
     * @param query 分页与关键字条件
     * @return 分页结果；页码越界时 {@code items} 为空列表而 {@code total} 仍为真实总数
     */
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
        // 批量 IN 前判空短路，避免空集合进 SQL。
        // 姓名回填必须忽略租户过滤：在线会话本身是跨租户的（本接口挂在 OnlineUserController 的
        // @PlatformAccess 平台级语义下，平台管理员要看到全部租户的在线用户），而 sys_user
        // 不在 ypbin.tenant.ignore-tables 内，listByIds 会被租户行拦截器追加 tenant_id 条件——
        // 不忽略的话只有本租户用户能回填到姓名，他租户行的 realName 静默为 null。
        // 这里无需 @InterceptorIgnore：数据权限只在 @DataPermission 作用域内才拼条件，本方法未标注。
        Map<Long, String> realNameById = ids.isEmpty() ? Map.of()
            : TenantContext.executeIgnore(() -> listByIds(ids)).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
        List<OnlineUserResp> items = pageUsers.stream().map(u -> {
            OnlineUserResp r = new OnlineUserResp();
            BeanUtils.copyProperties(u, r);
            r.setRealName(realNameById.get(u.getUserId()));
            return r;
        }).toList();
        return PageResult.of(items, total, page, pageSize);
    }
}
