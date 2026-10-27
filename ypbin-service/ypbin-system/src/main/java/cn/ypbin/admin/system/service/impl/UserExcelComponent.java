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
import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserPasswordHistory;
import cn.ypbin.admin.system.enums.GenderEnum;
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.vo.UserExportVo;
import cn.ypbin.admin.system.model.vo.UserImportResult;
import cn.ypbin.admin.system.model.vo.UserImportVo;
import cn.ypbin.admin.system.service.support.UserAccountSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.excel.util.ExcelUtils;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户 Excel 导入导出组件。
 *
 * <p>承载用户列表导出、导入模板下载与批量导入三个 Excel 能力，
 * 从 {@link SysUserServiceImpl} 拆分，保持单一职责。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@Component
@RequiredArgsConstructor
public class UserExcelComponent {

    private static final Logger log = LoggerFactory.getLogger(UserExcelComponent.class);

    /**
     * 单条多值 INSERT 的行数上限。
     *
     * <p>导入行数由上传文件决定（不可控），一次性拼进单条 INSERT 会有 {@code max_allowed_packet} 风险，
     * 故按固定大小分块——这是「分块批量写」而非逐行往返。</p>
     */
    private static final int INSERT_BATCH_SIZE = 500;

    /** 单次 IN 查询的元素上限（避免 SQL 过长/参数过多） */
    private static final int QUERY_BATCH_SIZE = 500;

    /** 导出列时间格式 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final UserAccountSupport accountSupport;

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

        List<SysUser> users = userMapper.selectList(wrapper);
        List<Long> deptIds = users.stream()
            .map(SysUser::getDeptId)
            .filter(d -> d != null && d > 0)
            .distinct()
            .toList();

        Map<Long, String> deptMap = deptIds.isEmpty() ? Map.of() :
            deptMapper.selectByIds(deptIds).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (k1, k2) -> k1));

        List<UserExportVo> list = users.stream().map(u -> {
            UserExportVo vo = new UserExportVo();
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setNickname(u.getNickname());
            vo.setDeptName(u.getDeptId() != null ? deptMap.getOrDefault(u.getDeptId(), "-") : "-");
            vo.setPhone(u.getPhone());
            vo.setEmail(u.getEmail());
            vo.setGender(GenderEnum.descOf(u.getGender()));
            vo.setStatus(UserStatusEnum.descOf(u.getStatus()));
            vo.setCreateTime(u.getCreateTime() != null ? u.getCreateTime().format(DATE_TIME_FORMATTER) : "");
            return vo;
        }).toList();

        ExcelUtils.export(response, "用户列表", UserExportVo.class, list);
    }

    public void downloadImportTemplate(HttpServletResponse response) {
        ExcelUtils.exportTemplate(response, "用户批量导入模板", UserImportVo.class);
    }

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

        // 只为「本次文件里出现的」用户名/手机号做一次批量查重：
        // 此前为判重把全表两列拉进内存，用户量大时是白付的全表扫描与内存占用
        Set<String> fileUsernames = list.stream().map(UserImportVo::getUsername)
            .filter(StringUtils::hasText).map(String::trim).collect(Collectors.toSet());
        Set<String> filePhones = list.stream().map(UserImportVo::getPhone)
            .filter(StringUtils::hasText).map(String::trim).collect(Collectors.toSet());
        Set<String> existingUsernames = loadExistingValues(fileUsernames, true);
        Set<String> existingPhones = loadExistingValues(filePhones, false);
        // 记录本文件内已成功导入的用户名/手机号，拦截文件内重复
        Set<String> importedUsernames = new HashSet<>();
        Set<String> importedPhones = new HashSet<>();
        // 待落库的用户：循环内只做校验与构建，落库在循环外分块批量写（避免 N 行 = N 次往返）
        List<SysUser> pending = new ArrayList<>();
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

            // 不再提供「默认口令」：批量导入的用户也必须走与 createUser 相同的密码策略，
            // 否则一次导入就能制造一批已知弱口令账号。空密码直接按行失败并提示补填。
            if (!StringUtils.hasText(vo.getPassword())) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailureMessages().add("第 " + rowNum + " 行：初始密码不能为空");
                continue;
            }
            String rawPassword = vo.getPassword().trim();
            try {
                accountSupport.validatePassword(rawPassword, username);
            } catch (BusinessException e) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getFailureMessages().add("第 " + rowNum + " 行：" + e.getMessage());
                continue;
            }
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setRealName(vo.getRealName().trim());
            user.setNickname(vo.getRealName().trim());
            user.setPassword(PasswordEncoderUtil.encode(rawPassword));
            user.setPwdResetTime(LocalDateTime.now());
            user.setUserType(AdminConstants.USER_TYPE_TENANT);
            user.setTenantId(currentTenantId);
            user.setStatus(UserStatusEnum.ENABLED.getCode());
            user.setPhone(phone);
            if (StringUtils.hasText(vo.getEmail())) {
                user.setEmail(vo.getEmail().trim());
            }
            pending.add(user);
            importedUsernames.add(username);
            if (phone != null) {
                importedPhones.add(phone);
            }
            result.setSuccessCount(result.getSuccessCount() + 1);
        }
        insertInBatches(pending);
        // 初始密码必须补记历史：导入是直插 sys_user 的批量路径，绕过了 createUser；
        // 不补记的话，用户在导入后第一次改密时查不到这条密码，「不得与最近 N 次重复」会放行
        recordInitialPasswordHistories(pending);
        // 缓存清空必须在落库之后：SysCache 回源带「空值哨兵」防穿透缓存，
        // 若先清缓存后落库，导入窗口内的登录/短信登录会把「用户不存在」写进缓存且此后不再清理
        evictImportedUserCaches(pending);
        return result;
    }

    /**
     * 落库后清理导入用户的 username / phone 永久缓存键（导入直插绕过 createUser，防旧快照残留）。
     *
     * @param users 已落库用户
     */
    private void evictImportedUserCaches(List<SysUser> users) {
        SysCache.evictUsersByUsernameAndPhone(
            users.stream().map(SysUser::getUsername).toList(),
            users.stream().map(SysUser::getPhone).filter(Objects::nonNull).toList());
    }

    /**
     * 批量查询「本次文件里出现的」用户名或手机号是否已存在。
     *
     * @param candidates 待查值（文件内去重后）
     * @param byUsername true=按用户名查，false=按手机号查
     * @return 库中已存在的值集合
     */
    private Set<String> loadExistingValues(Set<String> candidates, boolean byUsername) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<String> existing = new HashSet<>();
        for (List<String> chunk : partition(candidates)) {
            List<SysUser> rows = TenantContext.executeIgnore(() -> userMapper.selectList(
                byUsername
                    ? new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getUsername)
                        .in(SysUser::getUsername, chunk)
                    : new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getPhone)
                        .in(SysUser::getPhone, chunk)));
            for (SysUser row : rows) {
                String value = byUsername ? row.getUsername() : row.getPhone();
                if (StringUtils.hasText(value)) {
                    existing.add(value);
                }
            }
        }
        return existing;
    }

    /** 按固定大小切分集合（避免 IN 参数过多） */
    private static List<List<String>> partition(Collection<String> values) {
        List<String> list = new ArrayList<>(values);
        List<List<String>> chunks = new ArrayList<>();
        for (int fromIndex = 0; fromIndex < list.size(); fromIndex += QUERY_BATCH_SIZE) {
            chunks.add(list.subList(fromIndex,
                Math.min(fromIndex + QUERY_BATCH_SIZE, list.size())));
        }
        return chunks;
    }

    /**
     * 按固定批次批量写入用户。
     *
     * <p>整个导入方法处于同一事务（{@code @Transactional(rollbackFor = Exception.class)}），
     * 因此批量写与逐行写在成败语义上等价：任一行失败都会整体回滚，本就不存在「前面已插入」的部分成功。
     * 主键与 create_user/create_time 由 MyBatis-Plus 参数处理阶段回填（与内置 insert 同一机制），
     * 故循环内不再需要 {@code userMapper.insert}。</p>
     *
     * @param users 待落库用户（非空）
     */
    private void insertInBatches(List<SysUser> users) {
        for (int fromIndex = 0; fromIndex < users.size(); fromIndex += INSERT_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + INSERT_BATCH_SIZE, users.size());
            userMapper.insertBatch(users.subList(fromIndex, toIndex));
        }
    }

    /**
     * 为导入用户补记初始密码历史（与 {@code createUser} 同口径）。
     *
     * <p>导入直插 sys_user 并带回填的主键，故这里在落库之后按用户 ID 构造历史行；
     * 行数由上传文件决定（不可控），按 {@code INSERT_BATCH_SIZE} 分块批量写。</p>
     *
     * @param users 已落库用户（主键已回填）
     */
    private void recordInitialPasswordHistories(List<SysUser> users) {
        for (int fromIndex = 0; fromIndex < users.size(); fromIndex += INSERT_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + INSERT_BATCH_SIZE, users.size());
            List<SysUserPasswordHistory> rows = new ArrayList<>(toIndex - fromIndex);
            for (SysUser user : users.subList(fromIndex, toIndex)) {
                SysUserPasswordHistory row = new SysUserPasswordHistory();
                row.setUserId(user.getId());
                row.setPassword(user.getPassword());
                rows.add(row);
            }
            accountSupport.recordPasswordHistoryBatch(rows);
        }
    }

    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }
}
