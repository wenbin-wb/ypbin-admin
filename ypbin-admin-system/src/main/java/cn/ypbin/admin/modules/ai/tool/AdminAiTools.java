/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.tool;

import cn.ypbin.admin.modules.system.entity.SysJob;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.enums.JobStatusEnum;
import cn.ypbin.admin.modules.system.enums.UserStatusEnum;
import cn.ypbin.admin.modules.system.mapper.SysJobMapper;
import cn.ypbin.admin.modules.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 管理平台内置 AI 工具集。
 *
 * <p>业务方可在任意 Spring Bean 的方法上标注 {@code @Tool}，由 starter-ai 的
 * {@code AiToolAutoConfiguration} 自动发现并注册给 ChatClient。
 *
 * <p>工具运行于 Reactor 工作线程，租户上下文由 {@code TenantThreadLocalAccessor} +
 * {@code Hooks.enableAutomaticContextPropagation} 自动传播，无需手动绑定。
 * 禁止在工具方法内调用 {@code StpUtil} / {@code UserContext}，Sa-Token 上下文
 * 依赖 HTTP 请求线程，在 Reactor 工作线程会抛 {@code SaTokenContextException}。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ypbin.ai", name = "enabled", havingValue = "true")
public class AdminAiTools {

    private final SysUserMapper userMapper;
    private final SysJobMapper jobMapper;

    /**
     * 按用户名或真实姓名查找用户信息。
     *
     * @param keyword 用户名或真实姓名关键词
     * @return 匹配的用户列表（最多 10 条），含 ID、用户名、真实姓名、状态
     */
    @Tool(name = "searchUser",
        description = "按用户名或真实姓名搜索系统用户，返回用户基本信息，限制 10 条")
    public String searchUser(String keyword) {
        List<SysUser> users = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .like(SysUser::getUsername, keyword)
                .or()
                .like(SysUser::getRealName, keyword)
                .last("LIMIT 10"));
        if (users.isEmpty()) {
            return "未找到匹配用户：" + keyword;
        }
        return users.stream()
            .map(u -> String.format("ID=%s 用户名=%s 姓名=%s 状态=%s",
                u.getId(), u.getUsername(), u.getRealName(),
                UserStatusEnum.descOf(u.getStatus())))
            .collect(Collectors.joining("\n"));
    }

    /**
     * 查询定时任务列表及运行状态。
     *
     * @param nameKeyword 任务名称关键词（空字符串或 null 时返回全部，最多 20 条）
     * @return 任务列表：名称、执行器、Cron、状态
     */
    @Tool(name = "listJobs",
        description = "查询系统定时任务列表，可按名称筛选，返回任务名称/执行器/Cron/状态")
    public String listJobs(String nameKeyword) {
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<>();
        if (nameKeyword != null && !nameKeyword.isBlank()) {
            wrapper.like(SysJob::getName, nameKeyword);
        }
        wrapper.last("LIMIT 20");
        List<SysJob> jobs = jobMapper.selectList(wrapper);
        if (jobs.isEmpty()) {
            return "没有找到匹配的定时任务";
        }
        return jobs.stream()
            .map(j -> String.format("名称=%s 执行器=%s Cron=%s 状态=%s",
                j.getName(), j.getExecutor(),
                j.getCron() != null ? j.getCron() : ("每" + j.getFixedRateSeconds() + "秒"),
                JobStatusEnum.descOf(j.getStatus())))
            .collect(Collectors.joining("\n"));
    }

    /**
     * 获取系统基础统计数据。
     *
     * @return 正常用户数和运行中定时任务数
     */
    @Tool(name = "getSystemStats",
        description = "获取当前系统基础统计：注册用户总数、已启用任务数")
    public String getSystemStats() {
        long userCount = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, UserStatusEnum.ENABLED.getCode()));
        long runningJobs = jobMapper.selectCount(
            new LambdaQueryWrapper<SysJob>().eq(SysJob::getStatus, JobStatusEnum.ENABLED.getCode()));
        return String.format("系统统计 - 正常用户数: %d，运行中定时任务: %d", userCount, runningJobs);
    }
}
