/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.ai.tool;

import cn.ypbin.admin.modules.ai.core.AiChatUserContext;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.enums.UserStatusEnum;
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

    /**
     * 按用户名或真实姓名查找用户信息。
     *
     * @param keyword 用户名或真实姓名关键词
     * @return 匹配的用户列表（最多 10 条），含 ID、用户名、真实姓名、状态
     */
    @Tool(name = "searchUser",
        description = "按用户名或真实姓名搜索系统用户，返回用户基本信息，限制 10 条")
    public String searchUser(String keyword) {
        String denied = adminOnlyHint();
        if (denied != null) {
            return denied;
        }
        List<SysUser> users = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                // OR 必须分组：租户插件会在 WHERE 末尾追加 AND tenant_id = ?，
                // 不包一层 and(...) 时会拼成 username LIKE ? OR (real_name LIKE ? AND tenant_id = ?)，
                // 用户名的匹配就能越过租户边界（跨租户泄露）。
                .and(wrapper -> wrapper
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword))
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
     * 获取系统基础统计数据。
     *
     * @return 注册用户总数
     */
    @Tool(name = "getSystemStats",
        description = "获取当前系统基础统计：注册用户总数")
    public String getSystemStats() {
        String denied = adminOnlyHint();
        if (denied != null) {
            return denied;
        }
        long userCount = userMapper.selectCount(
            new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, UserStatusEnum.ENABLED.getCode()));
        return String.format("系统统计 - 正常用户数: %d", userCount);
    }

    /**
     * 管理端会话校验。
     *
     * <p>公开分享/挂件问答不会写入 {@link AiChatUserContext}，这里据此拒绝，
     * 避免匿名访客借 AI 枚举知识库所属租户的用户。</p>
     *
     * @return 拒绝提示；管理端会话返回 {@code null}
     */
    private static String adminOnlyHint() {
        if (AiChatUserContext.getUserId().isEmpty()) {
            return "该工具仅在管理端登录会话中可用";
        }
        return null;
    }
}
