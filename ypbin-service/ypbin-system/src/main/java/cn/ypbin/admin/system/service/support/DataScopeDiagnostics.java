/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

/**
 * 数据范围的诊断文案（宿主特有，与 starter 算法无关）。
 *
 * <p><b>为什么单独一个类</b>：这段文案是<b>历史缺陷的修复产物</b>——早期版本建议用户改用
 * {@code @DataPermission(ignore = true)}，但该机制在「外层已激活数据权限」时<b>不生效</b>
 * （{@code DataPermissionContext} 没有挂起语义，切面命中 ignore 只是自己不再 enter），
 * 用户照做问题依旧。改为指向语句级 {@code @InterceptorIgnore(dataPermission = "true")} 后，
 * 诊断才真正可操作。文案由 {@code AdminDataScopeHandlerTest} 直接断言（改回旧文案即转红），
 * 因此抽成常量而不是散落在方法里。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
final class DataScopeDiagnostics {

    /**
     * 取不到网关身份头时的 error 文案（{@code %s} 为 mappedStatementId 与表名）。
     *
     * <p>刻意<b>不</b>建议 {@code @DataPermission(ignore = true)}：外层已激活数据权限时它无效。</p>
     */
    static final String NO_IDENTITY_MESSAGE =
        "数据范围解析失败：当前请求没有网关签发的身份头（取不到用户 ID），"
            + "已按「拒绝全部」处理以免读到全量数据；若该路径本不应受数据权限约束，"
            + "请在**该条查询所在的 Mapper 语句**上加 @InterceptorIgnore(dataPermission = \"true\")，"
            + "或改用独立 Mapper 语句/不经 Mapper 的通道（注意：外层已激活数据权限时，"
            + "内层 @DataPermission(ignore = true) 不会生效）。mappedStatementId={}, table={}";

    private DataScopeDiagnostics() {
    }
}
