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
import cn.ypbin.admin.system.enums.GenderEnum;
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.vo.UserExportVo;
import cn.ypbin.admin.system.model.vo.UserImportResult;
import cn.ypbin.admin.system.model.vo.UserImportVo;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    /** 导入默认初始密码（行内未填密码时使用） */
    private static final String DEFAULT_PASSWORD = "123456";

    /** 导出列时间格式 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;

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

        // 预加载库中已有用户名/手机号到内存判重，避免逐行查库（用户名/手机号全局唯一）
        Set<String> existingUsernames = TenantContext.executeIgnore(() ->
            userMapper.selectList(new LambdaQueryWrapper<SysUser>().select(SysUser::getUsername))
                .stream().map(SysUser::getUsername).collect(Collectors.toSet()));
        Set<String> existingPhones = TenantContext.executeIgnore(() ->
            userMapper.selectList(new LambdaQueryWrapper<SysUser>().select(SysUser::getPhone))
                .stream().map(SysUser::getPhone).filter(StringUtils::hasText).collect(Collectors.toSet()));
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

            String rawPassword = StringUtils.hasText(vo.getPassword()) ? vo.getPassword().trim() : DEFAULT_PASSWORD;
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setRealName(vo.getRealName().trim());
            user.setNickname(vo.getRealName().trim());
            user.setPassword(PasswordEncoderUtil.encode(rawPassword));
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
            // 导入直插绕过 createUser，需同步清 username/phone 永久缓存键防旧快照残留
            SysCache.evictUser(null, username);
            SysCache.evictUserByPhone(phone);
            result.setSuccessCount(result.getSuccessCount() + 1);
        }
        insertInBatches(pending);
        return result;
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

    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }
}
