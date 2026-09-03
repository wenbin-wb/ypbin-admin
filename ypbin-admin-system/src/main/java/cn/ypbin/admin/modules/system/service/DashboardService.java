/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计服务。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface DashboardService {

    /**
     * 系统概览计数：用户 / 角色 / 部门 / 菜单 / 在线用户 / 操作日志。
     *
     * @return 键为 userCount/roleCount/deptCount/menuCount/onlineCount/logCount 的计数映射
     */
    Map<String, Object> stats();
}
