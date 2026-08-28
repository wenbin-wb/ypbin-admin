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
import cn.ypbin.admin.system.entity.SysUser;
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

    /** 导入默认初始密码（行内未填密码时使用） */
    private static final String DEFAULT_PASSWORD = "123456";

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
            user.setStatus(1);
            user.setPhone(phone);
            if (StringUtils.hasText(vo.getEmail())) {
                user.setEmail(vo.getEmail().trim());
            }
            userMapper.insert(user);
            importedUsernames.add(username);
            if (phone != null) {
                importedPhones.add(phone);
            }
            result.setSuccessCount(result.getSuccessCount() + 1);
        }
        return result;
    }

    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }
}
