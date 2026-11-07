/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysDept;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 部门 Mapper。
 *
 * <p><b>递归 CTE 语句必须显式跳过租户插件</b>（{@code @InterceptorIgnore(tenantLine = "true")}）
 * 并在 SQL 里自己写租户条件：租户插件按 SQL 里的表名注入条件，认不出
 * {@code WITH RECURSIVE dept_chain} 是 CTE，会把 CTE 名当表追加 {@code tenant_id}，
 * 运行时直接 {@code Unknown column}（与 {@code SysSpaceMapper} 同源问题，已实测）。
 * 跳过插件不等于放弃隔离——SQL 的每个分支都显式带 {@code tenant_id = #{tenantId}}。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 用递归 CTE 一次取回指定部门的父链（不含自身），按「父 → 根」升序。
     *
     * <p>部门新增/修改前的层级校验只需要这一条链；此前的实现把整张 {@code sys_dept}
     * 拉进内存再上溯，部门表随租户增长后是每次写操作都白付的一次全表扫描。
     * 单条语句也避免了「查一层、再查下一层」的循环内 DB 调用。</p>
     *
     * @param deptId   起始部门 ID（父部门）
     * @param tenantId 当前租户 ID（显式过滤，见类注释）
     * @param maxDepth CTE 递归深度上限（业务上限 + 余量）
     * @return 父链行（{@code id}/{@code pid}），按 depth 升序
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH RECURSIVE dept_chain AS (
            SELECT id, pid, 0 AS depth
            FROM sys_dept
            WHERE id = #{deptId} AND tenant_id = #{tenantId} AND is_deleted = 0
            UNION ALL
            SELECT d.id, d.pid, c.depth + 1
            FROM sys_dept d
            INNER JOIN dept_chain c ON d.id = c.pid
            WHERE d.tenant_id = #{tenantId} AND d.is_deleted = 0 AND c.depth < #{maxDepth}
        )
        SELECT id, pid FROM dept_chain ORDER BY depth ASC
        """)
    List<Map<String, Object>> selectParentChain(@Param("deptId") Long deptId,
        @Param("tenantId") Long tenantId, @Param("maxDepth") int maxDepth);

    /**
     * 用递归 CTE 取指定部门整棵子树的<b>最大相对深度</b>（自身为 0）。
     *
     * <p>移动部门时只校验「自身新深度」不够：拖着一棵深子树挂到更深的父部门下，
     * 自身可能没过线，最深的后代却超了上限——此后该子树里任何新增/调整都会被
     * 层级校验拒绝，树永久处于非法状态。与空间树同口径（{@code SysSpaceMapper}）。
     * 租户插件同样认不出 CTE，故跳过并显式写租户条件。</p>
     *
     * @param deptId   子树根部门 ID
     * @param tenantId 当前租户 ID
     * @param maxDepth CTE 递归深度上限（业务上限 + 余量）
     * @return 最大相对深度；部门不存在时返回 {@code null}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
        WITH RECURSIVE subtree AS (
            SELECT id, 0 AS relDepth
            FROM sys_dept
            WHERE id = #{deptId} AND tenant_id = #{tenantId} AND is_deleted = 0
            UNION ALL
            SELECT d.id, s.relDepth + 1
            FROM sys_dept d
            INNER JOIN subtree s ON d.pid = s.id
            WHERE d.tenant_id = #{tenantId} AND d.is_deleted = 0 AND s.relDepth < #{maxDepth}
        )
        SELECT MAX(relDepth) FROM subtree
        """)
    Integer selectSubtreeMaxRelDepth(@Param("deptId") Long deptId,
        @Param("tenantId") Long tenantId, @Param("maxDepth") int maxDepth);
}
