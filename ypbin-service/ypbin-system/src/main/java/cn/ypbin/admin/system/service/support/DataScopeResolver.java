/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.service.support;

import org.jspecify.annotations.Nullable;

/**
 * 数据范围解析能力（本仓数据范围口径的唯一入口）。
 *
 * <p><strong>为什么需要这个接口</strong>：数据范围有两类消费方——读路径上把范围条件拼进 SQL 的
 * starter 端口适配器，以及写路径上校验 {@code deptId} 入参的业务服务
 * （{@code SysUserServiceImpl#createUser}/{@code #updateUser}）。二者必须共用同一份口径，
 * 否则必然出现「读得到却写不进去」或「写得进去却读不到」。本接口把这份口径定义成
 * <strong>与 starter 端口无关的业务能力</strong>：业务服务只依赖本接口，
 * 端口适配职责留在 starter 端口实现侧（{@code provider} 包），依赖方向由
 * {@code service/impl → service/support} 单向收敛，不再出现 {@code service/impl → provider}。</p>
 *
 * <p><strong>两个方法共享完全相同的语义</strong>：</p>
 * <ol>
 *   <li>取不到网关签发的身份头 ⇒ 拒绝（读路径给出拒绝全部的条件片段，写路径返回 {@code false}）
 *       并记 error；<strong>不放行全量、不静默返回空</strong>。</li>
 *   <li>平台超级管理员（{@code SysPermissionService#isSuperAdmin}）⇒ 不受限
 *       （读路径不加条件，写路径恒 {@code true}）。</li>
 *   <li>其余用户按角色数据范围取并集：「全部」⇒ 不受限；「本部门及以下」与「自定义」展开部门树后代；
 *       「仅本人」只贡献自身条件（读路径 {@code id = 当前用户}）。</li>
 *   <li>无有效角色、或部门范围角色但用户没有部门 ⇒ 一律拒绝（fail-closed，禁静默放行）。</li>
 * </ol>
 *
 * <p><strong>只治理 {@code sys_user} 一张表</strong>：读路径端口按表名回调，其余表没有
 * {@code dept_id} 列，强行拼条件会产生 SQL 错误，故由实现侧做表白名单。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
public interface DataScopeResolver {

    /**
     * 解析读路径的数据范围 SQL 条件片段（供 starter {@code DataScopeHandler} 端口适配器调用）。
     *
     * @param mappedStatementId Mapper 方法全限定名
     * @param tableName         当前查询主表名
     * @return SQL 条件片段（不含 {@code WHERE} 关键字）；返回 {@code null} 表示当前查询不做范围限制
     */
    @Nullable
    String resolveCondition(String mappedStatementId, String tableName);

    /**
     * 判定写路径的目标部门是否落在<b>当前操作者</b>的数据范围内
     * （供 {@code SysUserServiceImpl#createUser}/{@code #updateUser} 的 {@code deptId} 入参校验）。
     *
     * <p>读路径的 {@code @DataPermission} 只把范围条件拼进被标注方法内已发出的 SQL，
     * 而写方法里的 {@code deptId} 是请求入参、不经过任何查询，因此写路径必须显式调用本方法；
     * 且必须与读路径同口径，否则管理员可把用户建/改到自己读不到的部门。</p>
     *
     * @param deptId 目标部门 ID，可为 {@code null}（表示不设部门）
     * @return 在数据范围内返回 {@code true}；越界、取不到身份、目标部门为空一律返回 {@code false}
     */
    boolean isDeptWithinScope(@Nullable Long deptId);
}
