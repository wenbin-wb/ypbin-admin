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

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.starter.json.ref.RefTextProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 引用翻译数据源集合：用户名称、部门名称。
 *
 * <p>每个实现对应一个 {@code @RefText("type")}，由 starter 的列表预加载机制批量查询，
 * 零 N+1。命名上不暴露 vben/frontend 等前端词语。</p>
 *
 * @author wenbin
 * @since 2026-08-02
 */
public class DbRefTextProviders {

    private DbRefTextProviders() {
    }

    /**
     * 用户引用翻译：类型 "user"，ID→显示名（realName）。
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
            return userMapper.selectBatchIds(idList).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u.getRealName() != null ? u.getRealName() : u.getUsername()));
        }
    }

    /**
     * 部门引用翻译：类型 "dept"，ID→部门名称。
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
            return deptMapper.selectBatchIds(idList).stream()
                .collect(Collectors.toMap(d -> (Serializable) d.getId(), SysDept::getName));
        }
    }
}
