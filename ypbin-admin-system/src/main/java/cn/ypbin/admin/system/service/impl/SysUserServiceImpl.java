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
import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysPost;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserPasswordHistory;
import cn.ypbin.admin.system.entity.SysUserPost;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysPostMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
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
import cn.ypbin.admin.system.model.vo.UserExportVo;
import cn.ypbin.admin.system.model.vo.UserImportResult;
import cn.ypbin.admin.system.model.vo.UserImportVo;
import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.excel.util.ExcelUtils;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.security.password.policy.PasswordCheckResult;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final SysDeptMapper deptMapper;
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
        String normalizedPhone = normalizePhone(phone);
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
        String phone = normalizePhone(req.getPhone());
        checkPhoneUnique(phone, null);
        if (!StringUtils.hasText(req.getPassword())) {
            throw new BusinessException("新增用户必须设置密码");
        }
        validatePassword(req.getPassword(), req.getUsername());
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
        recordPasswordHistory(user.getId(), encoded);
        assignRolesInternal(user.getId(), req.getRoleIds());
        assignPosts(user.getId(), req.getPostIds());
    }

    @Override
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserSaveReq req) {
        SysUser existing = getManageableUser(id);
        checkUsernameUnique(req.getUsername(), id);
        String phone = normalizePhone(req.getPhone());
        checkPhoneUnique(phone, id);
        validateAssignments(existing, req.getRoleIds(), req.getPostIds());
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "roleIds", "postIds", "password", "phone");
        user.setId(id);
        // 密码留空表示不修改
        String encoded = null;
        if (StringUtils.hasText(req.getPassword())) {
            validatePassword(req.getPassword(), req.getUsername());
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
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser user = getManageableUser(id);
        if (id.equals(LoginHelper.getUserId()) && Integer.valueOf(0).equals(status)) {
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
    @DataPermission
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = getManageableUser(id);
        validateAssignments(user, roleIds, null);
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
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(ProfileUpdateReq req) {
        Long userId = LoginHelper.getUserId();
        String phone = normalizePhone(req.getPhone());
        checkPhoneUnique(phone, userId);
        SysUser user = new SysUser();
        BeanUtils.copyProperties(req, user, "phone");
        user.setId(userId);
        boolean updated = update(user, new LambdaUpdateWrapper<SysUser>()
            .eq(SysUser::getId, userId)
            .set(SysUser::getPhone, phone));
        if (!updated) {
            throw new BusinessException("个人资料更新失败");
        }
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

    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }

    private void checkPhoneUnique(String phone, Long excludeId) {
        if (phone == null) {
            return;
        }
        boolean exists = TenantContext.executeIgnore(() -> exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getPhone, phone)
            .ne(excludeId != null, SysUser::getId, excludeId)));
        if (exists) {
            throw new BusinessException("手机号已存在：" + phone);
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
            .eq(SysRole::getStatus, 1));
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
            .eq(SysPost::getStatus, 1));
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

    @Override
    @DataPermission
    public void exportUsers(UserQuery query, HttpServletResponse response) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUserType, AdminConstants.USER_TYPE_TENANT)
            .like(StringUtils.hasText(query.getUsername()), SysUser::getUsername, query.getUsername())
            .like(StringUtils.hasText(query.getRealName()), SysUser::getRealName, query.getRealName())
            .like(StringUtils.hasText(query.getPhone()), SysUser::getPhone, query.getPhone())
            .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
            .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
            .orderByDesc(SysUser::getCreateTime);

        List<SysUser> users = list(wrapper);
        List<Long> deptIds = users.stream()
            .map(SysUser::getDeptId)
            .filter(d -> d != null && d > 0)
            .distinct()
            .toList();

        Map<Long, String> deptMap = deptIds.isEmpty() ? Map.of() :
            deptMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (k1, k2) -> k1));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<UserExportVo> list = users.stream().map(u -> {
            UserExportVo vo = new UserExportVo();
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setNickname(u.getNickname());
            vo.setDeptName(u.getDeptId() != null ? deptMap.getOrDefault(u.getDeptId(), "-") : "-");
            vo.setPhone(u.getPhone());
            vo.setEmail(u.getEmail());
            vo.setGender(u.getGender() != null && u.getGender() == 1 ? "男" : (u.getGender() != null && u.getGender() == 2 ? "女" : "未知"));
            vo.setStatus(u.getStatus() != null && u.getStatus() == 1 ? "正常" : "禁用");
            vo.setCreateTime(u.getCreateTime() != null ? u.getCreateTime().format(formatter) : "");
            return vo;
        }).toList();

        ExcelUtils.export(response, "用户列表", UserExportVo.class, list);
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        UserImportVo sample = new UserImportVo();
        sample.setUsername("zhangsan");
        sample.setRealName("张三");
        sample.setPhone("13800000000");
        sample.setEmail("zhangsan@example.com");
        sample.setPassword("123456");
        ExcelUtils.export(response, "用户批量导入模板", UserImportVo.class, List.of(sample));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserImportResult importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException("只能上传 .xlsx 或 .xls 格式的 Excel 文件");
        }
        List<UserImportVo> list;
        try {
            list = ExcelUtils.read(file.getInputStream(), UserImportVo.class);
        } catch (Exception e) {
            throw new BusinessException("读取 Excel 文件失败：" + e.getMessage());
        }
        if (list == null || list.isEmpty()) {
            throw new BusinessException("Excel 文件中未读取到有效数据");
        }

        UserImportResult result = new UserImportResult();
        result.setTotalCount(list.size());

        int rowNum = 1;
        Long currentTenantId = UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法确定当前租户"));

        // 预加载库中已有用户名/手机号到内存判重，避免逐行查库（用户名/手机号全局唯一）
        Set<String> existingUsernames = TenantContext.executeIgnore(() ->
            list(new LambdaQueryWrapper<SysUser>().select(SysUser::getUsername))
                .stream().map(SysUser::getUsername).collect(Collectors.toSet()));
        Set<String> existingPhones = TenantContext.executeIgnore(() ->
            list(new LambdaQueryWrapper<SysUser>().select(SysUser::getPhone))
                .stream().map(SysUser::getPhone).filter(StringUtils::hasText).collect(Collectors.toSet()));
        // 记录本文件内已成功导入的用户名/手机号，拦截文件内重复
        Set<String> importedUsernames = new HashSet<>();
        Set<String> importedPhones = new HashSet<>();

        for (UserImportVo vo : list) {
            rowNum++;
            if (!StringUtils.hasText(vo.getUsername())) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailureMessages().add("第 " + rowNum + " 行：用户名不能为空");
                continue;
            }
            String username = vo.getUsername().trim();
            if (existingUsernames.contains(username) || importedUsernames.contains(username)) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailureMessages().add("第 " + rowNum + " 行：用户名 [" + username + "] 已存在");
                continue;
            }
            if (!StringUtils.hasText(vo.getRealName())) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailureMessages().add("第 " + rowNum + " 行：真实姓名不能为空");
                continue;
            }

            String phone = normalizePhone(vo.getPhone());
            if (phone != null && (existingPhones.contains(phone) || importedPhones.contains(phone))) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailureMessages().add("第 " + rowNum + " 行：手机号 [" + phone + "] 已被占用");
                continue;
            }

            String rawPassword = StringUtils.hasText(vo.getPassword()) ? vo.getPassword().trim() : "123456";
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setRealName(vo.getRealName().trim());
            user.setNickname(vo.getRealName().trim());
            user.setPassword(PasswordEncoderUtil.encode(rawPassword));
            user.setUserType(AdminConstants.USER_TYPE_TENANT);
            user.setTenantId(currentTenantId);
            user.setStatus(1);
            user.setPhone(phone);
            if (StringUtils.hasText(vo.getEmail())) {
                user.setEmail(vo.getEmail().trim());
            }
            save(user);
            importedUsernames.add(username);
            if (phone != null) {
                importedPhones.add(phone);
            }
            result.setSuccessCount(result.getSuccessCount() + 1);
        }
        return result;
    }
}
