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

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysMenu;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.service.DashboardService;
import cn.ypbin.admin.system.service.SysLogService;
import cn.ypbin.starter.security.online.OnlineUserService;
import cn.ypbin.starter.tenant.core.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 仪表盘统计服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysMenuMapper menuMapper;
    private final SysLogService logService;
    private final OnlineUserService onlineUserService;

    @Override
    public Map<String, Object> stats() {
        return TenantContext.executeIgnore(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userCount", userMapper.selectCount(null));
            data.put("roleCount", roleMapper.selectCount(null));
            data.put("deptCount", deptMapper.selectCount(null));
            data.put("menuCount", menuMapper.selectCount(null));
            data.put("onlineCount", onlineUserService.count());
            data.put("logCount", logService.count());
            return data;
        });
    }
}
