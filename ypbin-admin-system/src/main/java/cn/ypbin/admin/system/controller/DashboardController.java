/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysMenuMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.model.resp.LogResp;
import cn.ypbin.admin.system.model.resp.LogTrendResp;
import cn.ypbin.admin.system.service.SysLogService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.security.online.OnlineUserService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘统计接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController extends BaseController {

    private final SysUserMapper userMapper;

    private final SysRoleMapper roleMapper;

    private final SysDeptMapper deptMapper;

    private final SysMenuMapper menuMapper;

    private final SysLogService logService;

    private final OnlineUserService onlineUserService;

    /**
     * 系统概览计数：用户 / 角色 / 部门 / 菜单 / 在线用户 / 操作日志。
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("roleCount", roleMapper.selectCount(null));
        data.put("deptCount", deptMapper.selectCount(null));
        data.put("menuCount", menuMapper.selectCount(null));
        data.put("onlineCount", onlineUserService.count());
        data.put("logCount", logService.count());
        return ok(data);
    }

    /**
     * 最新动态：最近若干条操作日志。
     *
     * @param limit 条数，默认 10
     */
    @GetMapping("/latest-logs")
    public R<List<LogResp>> latestLogs(@RequestParam(defaultValue = "10") int limit) {
        return ok(logService.latestLogs(limit));
    }

    /**
     * 操作日志趋势：近若干天按天聚合。
     *
     * @param days 天数，默认 7
     */
    @GetMapping("/log-trend")
    public R<List<LogTrendResp>> logTrend(@RequestParam(defaultValue = "7") int days) {
        return ok(logService.logTrend(days));
    }
}
