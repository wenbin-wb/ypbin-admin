/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import cn.ypbin.admin.system.entity.SysAuthTemplate;
import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysAuthTemplateMapper;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.starter.json.ref.RefTextProvider;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 引用字段翻译数据源（数据库实现）。
 *
 * <p>starter 的 {@code @RefText} 机制是"有 provider 才装配翻译管理器"（{@code JacksonAutoConfiguration}
 * 里对 {@code RefTextProvider} 做了 {@code @ConditionalOnBean}）：<strong>没有 provider 时带
 * {@code @RefText} 的字段不会输出派生值，也不会报错</strong>。本类提供 {@code user} / {@code dept} /
 * {@code template} 三类数据源，使这些字段真正产出 {@code xxxName}。</p>
 *
 * <p>批量翻译由框架按类型聚合后一次调用本类，因此这里一律用 {@code selectByIds} 批量取数，
 * 不在循环里查库。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public final class DbRefTextProviders {

    private DbRefTextProviders() {
    }

    /**
     * 用户引用翻译：类型 {@code user}，ID → 姓名（无姓名时回退登录名）。
     */
    @Component
    @RequiredArgsConstructor
    public static class UserName implements RefTextProvider {

        private final SysUserMapper userMapper;

        @Override
        public String type() {
            return "user";
        }

        @Override
        public Map<Object, String> getNames(Collection<Object> ids) {
            List<Long> idList = ids.stream()
                .map(id -> Long.valueOf(id.toString())).toList();
            return userMapper.selectByIds(idList).stream()
                .collect(Collectors.toMap(SysUser::getId,
                    user -> user.getRealName() == null || user.getRealName().isBlank()
                        ? user.getUsername() : user.getRealName()));
        }
    }

    /**
     * 部门引用翻译：类型 {@code dept}，ID → 部门名称。
     */
    @Component
    @RequiredArgsConstructor
    public static class DeptName implements RefTextProvider {

        private final SysDeptMapper deptMapper;

        @Override
        public String type() {
            return "dept";
        }

        @Override
        public Map<Object, String> getNames(Collection<Object> ids) {
            List<Long> idList = ids.stream()
                .map(id -> Long.valueOf(id.toString())).toList();
            return deptMapper.selectByIds(idList).stream()
                .collect(Collectors.toMap(dept -> (Serializable) dept.getId(), SysDept::getName));
        }
    }

    /**
     * 权限模板引用翻译：类型 {@code template}，ID → 模板名称。
     */
    @Component
    @RequiredArgsConstructor
    public static class TemplateName implements RefTextProvider {

        private final SysAuthTemplateMapper templateMapper;

        @Override
        public String type() {
            return "template";
        }

        @Override
        public Map<Object, String> getNames(Collection<Object> ids) {
            List<Long> idList = ids.stream()
                .map(id -> Long.valueOf(id.toString())).toList();
            return templateMapper.selectByIds(idList).stream()
                .collect(Collectors.toMap(template -> (Serializable) template.getId(),
                    SysAuthTemplate::getName));
        }
    }
}
