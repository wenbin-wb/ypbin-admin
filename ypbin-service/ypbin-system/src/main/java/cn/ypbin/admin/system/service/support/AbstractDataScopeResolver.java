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

import cn.ypbin.admin.system.entity.SysDept;
import cn.ypbin.admin.system.entity.SysRole;
import cn.ypbin.admin.system.entity.SysRoleDept;
import cn.ypbin.admin.system.enums.DataScopeEnum;
import cn.ypbin.admin.system.mapper.SysDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleDeptMapper;
import cn.ypbin.admin.system.mapper.SysRoleMapper;
import cn.ypbin.admin.system.service.SysPermissionService;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 数据范围解析算法的抽象实现（{@link DataScopeResolver} 的唯一实现体）。
 *
 * <p><strong>规则与端口解耦</strong>：Starter 的 {@code DataPermissionAutoConfiguration} 只提供
 * 「把 SQL 片段解析成 JSqlParser 表达式并并入 {@code WHERE}」的机制（{@code DataScopeMultiHandler}），
 * <strong>规则本身完全由宿主决定</strong>——即返回一段条件片段（如 {@code dept_id IN (1,2)}），
 * 返回 {@code null} 表示当前查询不做范围限制。本类按 {@code sys_role.data_scope} 计算该片段，
 * 并额外提供写路径的部门校验；starter 端口的具体绑定由 {@code provider} 包中的适配器子类完成。</p>
 *
 * <p><strong>作用范围（只治理 {@code sys_user} 一张表）</strong>：端口按「表名」回调，
 * 本类仅对 {@code sys_user} 返回片段，其余表（{@code sys_role}/{@code sys_user_role}/
 * {@code sys_post}/{@code sys_user_social} 等）一律返回 {@code null}——这些表没有 {@code dept_id} 列，
 * 强行拼条件会直接产生 SQL 错误。当前 {@code @DataPermission} 的 9 处使用点全部落在 {@code sys_user}
 * 及其关联表上，故这一白名单是完整的；新增使用点若涉及别的带部门列的表，需同步扩展。</p>
 *
 * <p><strong>语义（平台超管 / 租户 / 部门树如何组合）</strong>：</p>
 * <ol>
 *   <li>无网关身份头（取不到当前用户）⇒ <strong>拒绝全部</strong>（{@code id = -1}）并记 error。
 *       被标注的方法全部在带 {@code @SaCheckPermission} 的鉴权路径上，取不到身份属异常路径，
 *       此时「放行全部」会越权、「静默返回空」会掩盖问题，故显式拒绝并留痕（禁静默降级）。</li>
 *   <li>平台超级管理员（{@code SysPermissionService#isSuperAdmin}：平台用户 + {@code PLATFORM_SUPER}
 *       角色）⇒ 不加任何条件（租户隔离仍由租户拦截器独立施加，本类<strong>不</strong>拼 {@code tenant_id}）。</li>
 *   <li>其余用户按角色数据范围<strong>取并集（最大范围）</strong>：任一角色为「全部」⇒ 不加条件；
 *       否则把「本部门」「本部门及以下（含部门树后代）」「自定义（角色绑定部门 + 其后代）」
 *       汇集为 {@code dept_id IN (...)}，「仅本人」汇集为 {@code id = 当前用户}，两者以 {@code OR} 组合。</li>
 *   <li>无角色、或条件集合为空（典型是部门范围角色但用户没有部门）⇒ 拒绝全部并记 warn。</li>
 * </ol>
 *
 * <p><strong>为什么是抽象类而不是直接装配的 Bean</strong>：算法本身<b>不认识</b> starter 端口，
 * 装配入口应当只有一处——即 {@code provider} 包中实现 {@code DataScopeHandler} 的适配器子类。
 * 让适配器继承本类既保证「口径只有一份实现」，又避免同一能力出现两个 Bean 而让注入产生歧义；
 * 本类因此不带 {@code @Component}，Spring 只装配适配器。</p>
 *
 * <p><strong>为什么用 {@link ObjectProvider} 延迟取 Mapper</strong>：端口适配器参与构建 MyBatis 拦截器链，
 * 会在 {@code SqlSessionFactory} 初始化早期被创建，直接注入 Mapper 会形成循环依赖；本类只在请求处理
 * 时才真正调用，延迟解析安全（与单体版同一做法）。</p>
 *
 * <p><strong>代价（如实声明）</strong>：每次解析会触发 2～4 次查询——1 次超管判定 + 1 次角色 +
 * 按需 1 次部门树 + 按需 1 次角色-部门批量。未加缓存是有意为之：数据范围直接影响可见数据，
 * 缓存的失效点（角色/部门/用户角色变更）分散且遗漏即越权，本类优先正确性；如需降本，
 * 应把「按用户缓存 + 变更处失效」作为独立改动评估。</p>
 *
 * <p><strong>已知边界</strong>：① 端口只把表名交给宿主（{@code DataScopeHandler#getDataScopeSql} 签名
 * 无表别名），故生成的片段<strong>不带表别名前缀</strong>；当前使用点均为单表 {@code sys_user} 查询
 * （唯一的 {@code sys_user} 关联查询 {@code SysRoleMapper#selectByUserId} 发生在解析过程中，见下条），
 * 无歧义风险，但日后若在 {@code @DataPermission} 作用域内写「{@code sys_user} 与另一张也有
 * {@code dept_id} 的表 JOIN」的语句，会出现列名歧义，需改端口或改写 SQL。
 * ② 解析过程中的查询（角色/超管判定会 JOIN {@code sys_user}）会再次回调适配器，用
 * {@link #RESOLVING} 线程内标记短路返回 {@code null}，避免无限递归。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
public abstract class AbstractDataScopeResolver implements DataScopeResolver {

    /** 被治理的表（本仓带 {@code dept_id} 且被 {@code @DataPermission} 覆盖的业务表） */
    private static final String TARGET_TABLE = "sys_user";

    /** 部门列 */
    private static final String COLUMN_DEPT_ID = "dept_id";

    /** 主键列 */
    private static final String COLUMN_ID = "id";

    /** 拒绝全部的条件片段：主键不可能等于 -1，等价「查不到任何行」 */
    private static final String DENY_ALL = COLUMN_ID + " = -1";

    /**
     * 解析中标记：解析自身所需数据时会执行查询，这些查询若含 {@code sys_user}（超管判定与
     * {@code selectByUserId} 都 JOIN 了 {@code sys_user}）会再次回调适配器，形成无限递归。
     */
    private static final ThreadLocal<Boolean> RESOLVING = new ThreadLocal<>();

    /**
     * 日志器：按<b>运行时类</b>取名，使日志分类落在真正对外提供服务的适配器 Bean 上
     * （{@code provider} 包中的子类），而不是抽象基类——按 Bean 定位日志更直观，
     * 也保证既有按处理器类捕获日志的告警文案断言继续成立。
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ObjectProvider<SysRoleMapper> roleMapperProvider;

    private final ObjectProvider<SysRoleDeptMapper> roleDeptMapperProvider;

    private final ObjectProvider<SysDeptMapper> deptMapperProvider;

    private final ObjectProvider<SysPermissionService> permissionServiceProvider;

    protected AbstractDataScopeResolver(ObjectProvider<SysRoleMapper> roleMapperProvider,
        ObjectProvider<SysRoleDeptMapper> roleDeptMapperProvider,
        ObjectProvider<SysDeptMapper> deptMapperProvider,
        ObjectProvider<SysPermissionService> permissionServiceProvider) {
        this.roleMapperProvider = roleMapperProvider;
        this.roleDeptMapperProvider = roleDeptMapperProvider;
        this.deptMapperProvider = deptMapperProvider;
        this.permissionServiceProvider = permissionServiceProvider;
    }

    @Override
    public String resolveCondition(String mappedStatementId, String tableName) {
        if (!TARGET_TABLE.equalsIgnoreCase(tableName)) {
            return null;
        }
        if (Boolean.TRUE.equals(RESOLVING.get())) {
            // 本类解析过程中的查询（角色/超管判定）不再叠加数据范围，避免递归
            return null;
        }
        Long userId = IdentityContext.getUserId().orElse(null);
        if (userId == null) {
            // ⚠️ 修复指引必须指向「真正可行」的机制：此处**不能**建议 @DataPermission(ignore = true)。
            // 原因：数据权限是否生效由 DataPermissionContext（线程内计数）决定，而它只有 enter/exit/isActive、
            // **没有「挂起」语义**；DataPermissionAspect 命中 ignore=true 时只是自己不再 enter() 而直接
            // point.proceed()。因此当**外层**已激活数据权限时，内层方法再标 @DataPermission(ignore = true)
            // 并不能让上下文失效——本处理器依旧会被回调，用户照建议改完问题仍在，属误导。
            // 真正可行的三条路（按代价从低到高）：
            // ① 语句级 @InterceptorIgnore(dataPermission = "true")：只让**该条 SQL** 跳过数据权限拦截器
            //    （MyBatis-Plus 的 DataPermissionInterceptor#beforeQuery/#beforePrepare 首行即判
            //    InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())），作用面最小，首选；
            // ② 为免过滤场景单写一条**独立 Mapper 语句**并同样标注 @InterceptorIgnore，与业务查询隔离；
            // ③ 干脆**不经 Mapper**（走缓存/专用客户端），从根上不进入数据权限拦截器链。
            // 本仓既有范例：SysUserMapper#countByUsernameGlobal / #countByPhoneGlobal。
            log.error("数据范围解析失败：当前请求没有网关签发的身份头（取不到用户 ID），"
                + "已按「拒绝全部」处理以免读到全量数据；若该路径本不应受数据权限约束，"
                + "请在**该条查询所在的 Mapper 语句**上加 @InterceptorIgnore(dataPermission = \"true\")，"
                + "或改用独立 Mapper 语句/不经 Mapper 的通道（注意：外层已激活数据权限时，"
                + "内层 @DataPermission(ignore = true) 不会生效）。mappedStatementId={}, table={}",
                mappedStatementId, tableName);
            return DENY_ALL;
        }
        Long userDeptId = IdentityContext.getLoginUser()
            .map(LoginUser::getDeptId)
            .orElse(null);

        ResolvedScope scope;
        RESOLVING.set(Boolean.TRUE);
        try {
            if (permissionServiceProvider.getObject().isSuperAdmin(userId)) {
                return null;
            }
            scope = resolveScope(userId, userDeptId);
        } finally {
            RESOLVING.remove();
        }
        if (scope.unlimited()) {
            return null;
        }
        return buildCondition(scope, userId);
    }

    @Override
    public boolean isDeptWithinScope(@Nullable Long deptId) {
        Long userId = IdentityContext.getUserId().orElse(null);
        if (userId == null) {
            log.error("写路径数据范围校验失败：当前请求没有网关签发的身份头（取不到用户 ID），"
                + "已按「拒绝」处理（不放行全量）。targetDeptId={}", deptId);
            return false;
        }
        if (Boolean.TRUE.equals(RESOLVING.get())) {
            // 结构上不可达：本方法只在写方法里调用，而解析自身的查询走的是 resolveCondition（已短路）。
            // 保留兜底是为了万一将来被误用到解析链路里时「显式拒绝 + 留痕」，而不是无限递归或静默放行。
            log.error("写路径数据范围校验被重入（解析自身查询调用了本方法），已按「拒绝」处理。"
                + "targetDeptId={}", deptId);
            return false;
        }
        Long userDeptId = IdentityContext.getLoginUser()
            .map(LoginUser::getDeptId)
            .orElse(null);
        ResolvedScope scope;
        RESOLVING.set(Boolean.TRUE);
        try {
            if (permissionServiceProvider.getObject().isSuperAdmin(userId)) {
                return true;
            }
            scope = resolveScope(userId, userDeptId);
        } finally {
            RESOLVING.remove();
        }
        if (scope.unlimited()) {
            return true;
        }
        // deptId 为空时不允许：读路径条件是 dept_id IN (...)，NULL 不匹配任何部门，写进去就再也读不到
        boolean within = deptId != null && scope.deptIds().contains(deptId);
        if (!within) {
            log.warn("写路径数据范围校验未通过：userId={}, targetDeptId={}, 可见部门={}",
                userId, deptId, scope.deptIds());
        }
        return within;
    }

    /**
     * 解析当前用户的数据范围并集。
     *
     * @param userId     当前用户 ID
     * @param userDeptId 当前用户部门 ID（取自网关签发身份头，可能为空）
     * @return 解析结果
     */
    private ResolvedScope resolveScope(Long userId, Long userDeptId) {
        List<SysRole> roles = roleMapperProvider.getObject().selectByUserId(userId);
        if (roles.isEmpty()) {
            log.warn("当前用户没有任何有效角色，数据范围按「拒绝全部」处理：userId={}", userId);
            return new ResolvedScope(false, false, Set.of());
        }
        if (roles.stream().anyMatch(role -> DataScopeEnum.ALL.getCode().equals(role.getDataScope()))) {
            return new ResolvedScope(true, false, Set.of());
        }

        List<Long> customRoleIds = roles.stream()
            .filter(role -> DataScopeEnum.CUSTOM.getCode().equals(role.getDataScope()))
            .map(SysRole::getId)
            .toList();
        // 批量取角色-部门关联（禁循环内 DB：按角色逐个查会退化成 N+1），无自定义角色时判空短路
        Map<Long, List<Long>> customDeptIds = customRoleIds.isEmpty() ? Map.of()
            : roleDeptMapperProvider.getObject().selectDeptIdsByRoleIds(customRoleIds).stream()
                .collect(Collectors.groupingBy(SysRoleDept::getRoleId,
                    Collectors.mapping(SysRoleDept::getDeptId, Collectors.toList())));

        boolean needsTree = roles.stream().anyMatch(role ->
            DataScopeEnum.DEPT_AND_CHILD.getCode().equals(role.getDataScope())
                || DataScopeEnum.CUSTOM.getCode().equals(role.getDataScope()));
        Map<Long, List<SysDept>> pidIndex = needsTree ? buildPidIndex() : Map.of();

        Set<Long> deptIds = new LinkedHashSet<>();
        boolean self = false;
        boolean unlimited = false;
        for (SysRole role : roles) {
            Optional<DataScopeEnum> scope = DataScopeEnum.fromCode(role.getDataScope());
            if (scope.isEmpty()) {
                // 未知/空值属数据脏：不放大范围，但必须可见，否则会变成「该角色莫名看不到数据」
                log.warn("角色数据范围取值无法识别，该角色不贡献任何数据范围：roleId={}, dataScope={}",
                    role.getId(), role.getDataScope());
                continue;
            }
            switch (scope.get()) {
                case DEPT_AND_CHILD -> {
                    if (userDeptId != null) {
                        deptIds.add(userDeptId);
                        deptIds.addAll(collectDescendants(userDeptId, pidIndex));
                    }
                }
                case DEPT -> {
                    if (userDeptId != null) {
                        deptIds.add(userDeptId);
                    }
                }
                case CUSTOM -> {
                    for (Long boundDeptId : customDeptIds.getOrDefault(role.getId(), List.of())) {
                        deptIds.add(boundDeptId);
                        // 自定义部门同样向下展开：与部门树勾选语义一致（勾中某部门即含其下所有部门）
                        deptIds.addAll(collectDescendants(boundDeptId, pidIndex));
                    }
                }
                case SELF -> self = true;
                // 「全部」在进入循环前已提前返回；保留该分支只为覆盖全部枚举值（不可达）
                case ALL -> unlimited = true;
            }
        }
        return new ResolvedScope(unlimited, self, deptIds);
    }

    /**
     * 拼装最终条件片段。
     *
     * @param scope  解析结果
     * @param userId 当前用户 ID
     * @return SQL 条件片段
     */
    private String buildCondition(ResolvedScope scope, Long userId) {
        List<String> conditions = new ArrayList<>();
        if (!scope.deptIds().isEmpty()) {
            String deptList = scope.deptIds().stream().map(String::valueOf).collect(Collectors.joining(","));
            conditions.add(COLUMN_DEPT_ID + " IN (" + deptList + ")");
        }
        if (scope.self()) {
            conditions.add(COLUMN_ID + " = " + userId);
        }
        if (conditions.isEmpty()) {
            log.warn("数据范围解析结果为空（无有效角色，或角色都是部门范围但当前用户没有部门），"
                + "按「拒绝全部」处理：userId={}", userId);
            return DENY_ALL;
        }
        return conditions.size() == 1 ? conditions.get(0) : "(" + String.join(" OR ", conditions) + ")";
    }

    /**
     * 按父部门 ID 建立索引（一次全量查询换掉逐层查库）。
     *
     * @return 父部门 ID → 子部门列表
     */
    private Map<Long, List<SysDept>> buildPidIndex() {
        Map<Long, List<SysDept>> pidIndex = new HashMap<>();
        for (SysDept dept : deptMapperProvider.getObject().selectList(null)) {
            pidIndex.computeIfAbsent(dept.getPid(), key -> new ArrayList<>()).add(dept);
        }
        return pidIndex;
    }

    /**
     * 收集某部门的全部后代部门 ID（纯内存遍历，无逐层查库）。
     *
     * <p>用已访问集合兜底：{@code sys_dept} 只有 {@code pid} 单列，{@code updateDept} 仅拒绝
     * 「父部门是自己」，理论上仍可通过两次编辑造出环；无兜底时环会让递归栈溢出。</p>
     *
     * @param rootId  根部门 ID
     * @param pidIndex 父部门索引
     * @return 后代部门 ID 集合（不含 rootId 自身）
     */
    private Set<Long> collectDescendants(Long rootId, Map<Long, List<SysDept>> pidIndex) {
        Set<Long> descendants = new LinkedHashSet<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        visited.add(rootId);
        pending.add(rootId);
        while (!pending.isEmpty()) {
            Long current = pending.poll();
            for (SysDept child : pidIndex.getOrDefault(current, List.of())) {
                if (visited.add(child.getId())) {
                    descendants.add(child.getId());
                    pending.add(child.getId());
                }
            }
        }
        return descendants;
    }

    /**
     * 数据范围解析结果。
     *
     * @param unlimited 是否不受限（全部数据）
     * @param self      是否包含「仅本人」
     * @param deptIds   可见部门 ID 集合
     */
    private record ResolvedScope(boolean unlimited, boolean self, Set<Long> deptIds) {
    }
}
