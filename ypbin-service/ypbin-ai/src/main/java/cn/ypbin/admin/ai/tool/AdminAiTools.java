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

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.enums.UserStatusEnum;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;


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

    private final ISystemClient systemFeignClient;

    /**
     * 按用户名或真实姓名查找用户信息。
     *
     * @param keyword 用户名或真实姓名关键词
     * @return 匹配的用户列表（最多 10 条），含 ID、用户名、真实姓名、状态
     */
    @Tool(name = "searchUser",
        description = "按用户名或真实姓名搜索系统用户，返回用户基本信息，限制 10 条")
    public String searchUser(String keyword) {
        var resp = systemFeignClient.searchUsers(keyword);
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            return "系统服务暂不可用，请稍后重试";
        }
        List<SysUser> users = resp.getData();
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
     * 获取系统基础统计数据。
     *
     * @return 注册用户总数
     */
    @Tool(name = "getSystemStats",
        description = "获取当前系统基础统计：注册用户总数")
    public String getSystemStats() {
        String userCount = feignCount(systemFeignClient.countUsers(), "用户数");
        if (userCount.startsWith("ERROR:")) {
            return userCount;
        }
        return String.format("系统统计 - 正常用户数: %s", userCount);
    }

    private static String feignCount(R<Long> resp, String label) {
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            return "ERROR:系统服务暂不可用，无法获取" + label;
        }
        return String.valueOf(resp.getData());
    }
}
