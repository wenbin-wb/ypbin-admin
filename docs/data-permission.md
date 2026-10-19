# 数据范围（Data Permission）实现说明与迁移对照

> 适用分支：`main`（微服务版）。本文记录 `@DataPermission` 数据范围的**算法归属**、
> **迁移前后的语义对照**、以及**哪些部分刻意留在宿主**。
> 代码入口：`ypbin-service/ypbin-system` 的 `service/support/AbstractDataScopeResolver`。

## 1. 一句话结论

算法与机制**已在 `ypbin-starter-extension-datapermission`**（starter 4.1.0，issue #107）：
角色范围并集取最大、部门后代展开（含环防护）、SQL 片段拼装、重入防护、超管短路、
「拒绝全部」的读写双语义，**只有一份**。本仓只剩三件事：

| 留在宿主的部分 | 为什么不能上收 |
|---|---|
| **治理表白名单**（仅 `sys_user`） | 「哪些表有 `dept_id` 列」是业务表结构事实，starter 无法预设 |
| **5 个数据源方法** | 身份取自网关身份头、角色/部门查本仓 Mapper、超管判定是本仓权限模型 |
| **诊断文案** | 只有本仓知道「哪条修复路径才真正可行」（见 §4） |

## 2. 分层与依赖方向

```
service/impl (SysUserServiceImpl)
        │ 只依赖 ↓（架构规则：service/impl 不得依赖 provider）
service/support/DataScopeResolver   ← 业务接口（读 + 写两条路径共用同一口径）
        ↑
service/support/AbstractDataScopeResolver  ← 适配层：白名单 + 委托
        ├── extends starter AbstractDataScopeHandler （算法与机制）
        └── uses    DataScopeDataSources（Mapper 调用 + 脏数据留痕）
        ↑
provider/AdminDataScopeHandler      ← starter 端口 DataScopeHandler 的绑定（约 20 行）
```

`AbstractDataScopeResolver` 拆成「适配层（约定）」与「数据访问层（取数）」两个文件，是**职责分离**
而非凑行数：取数层改查询（加缓存、换批量语句）不影响适配层；适配层的白名单/文案变化不碰 SQL。

## 3. 迁移前后语义对照（逐条）

| # | 语义 | 迁移前（377 行自研） | 迁移后（starter 算法） | 结论 |
|---|---|---|---|---|
| 1 | 非治理表 | 直接返回 `null`，不取身份不查库 | 同（白名单在 `resolveCondition` 最前） | **一致** |
| 2 | 无网关身份头 | 拒绝全部 `id = -1` + error 文案 | 宿主先判身份、用本仓文案 + `denyAllSql` | **一致** |
| 3 | 平台超管 | 短路返回 `null`（写路径恒放行），不查角色 | starter 基类：`isSuperAdmin` 为真即返回 `null` | **一致** |
| 4 | 多角色取并集（最大范围） | 手动 switch + `LinkedHashSet` 累加 | `DataScopeCalculator.resolve` 同一语义 | **一致** |
| 5 | 「本部门及以下」展开后代 | 自写 `collectDescendants`（visited + 迭代） | starter 同名方法（同样 visited + 显式栈） | **一致** |
| 6 | 「自定义」部门 + 后代 | 批量查角色-部门，逐角色展开 | 同（宿主取数 + starter 展开） | **一致** |
| 7 | 部门数据成环 | 已访问集合兜底，不死循环 | starter 同样是「已访问集合 + 迭代」 | **一致**（有测试） |
| 8 | 脏 `data_scope` | 该角色不贡献 + 记 WARN | 同（映射为 `ScopeType.UNKNOWN`，宿主记 WARN） | **一致** |
| 9 | 无角色 | 拒绝全部 `id = -1` | 同 | **一致** |
| 10 | 部门范围角色但用户无部门（读） | 拒绝全部 `id = -1` | 同 | **一致** |
| 11 | 写路径 target 部门为 `null` | 拒绝 | starter `isDeptWithinScope`：`deptId != null` 才可能放行 | **一致** |
| 12 | 写路径部门归属 | 可见部门集合 `contains` | 同 | **一致** |
| 13 | 重入防护 | 自建 `ThreadLocal RESOLVING` | starter 基类同名机制 | **一致** |
| 14 | SQL 片段文本 | `dept_id IN (...)` / `id = N` / `(A OR B)` / `id = -1` | 同（列名走 `DEFAULT_*_COLUMN` 常量） | **一致** |

**结论：无行为变化。** 由 `AdminDataScopeHandlerTest`（18 条主路径）、
`DataScopeMigrationBoundaryTest`（10 条边界）、`SysUserServiceImplDeptScopeTest`（4 条写路径）锁定。

## 4. 诊断文案为什么留在宿主（历史缺陷修复的产物）

取不到身份头时的 error 文案**不能**沿用 starter 的通用版本，因为它必须告诉用户「哪条路真的可行」：

- ❌ 早期版本建议 `@DataPermission(ignore = true)` —— **该机制在外层已激活数据权限时不生效**
  （`DataPermissionContext` 没有挂起语义，切面命中 `ignore` 只是自己不再 `enter()`）；
- ✅ 正确做法是语句级 `@InterceptorIgnore(dataPermission = "true")`，或独立 Mapper 语句/不经 Mapper 的通道。

该文案由 `AdminDataScopeHandlerTest#shouldAdviseWorkingMechanismWhenIdentityMissing` 直接断言
（改回旧文案即转红）。文案常量在 `service/support/DataScopeDiagnostics`。

## 5. 已知边界

1. **片段不带表别名**：端口签名 `getDataScopeSql(mappedStatementId, tableName)` 没有别名信息。
   当前 9 处 `@DataPermission` 使用点均为单表 `sys_user` 查询，无歧义风险。
   若将来在作用域内写「`sys_user` 与另一张也有 `dept_id` 的表 JOIN」，会出现列名歧义。
2. **每次解析 2～4 次查询**（超管判定 + 角色 + 按需部门树 + 按需角色-部门批量），**未加缓存**：
   缓存失效点（角色/部门/用户角色变更）分散且遗漏即越权，优先正确性。降本应作为独立改动评估。
3. **治理表白名单是手工维护的**：新增带 `dept_id` 且需要数据范围治理的表时，必须同步改
   `AbstractDataScopeResolver.TARGET_TABLE`（当前是单值，多表需改为集合）。

## 6. 验收与回归

```bash
# 适配层行数（issue #107 验收 4：< 120 行）
wc -l ypbin-service/ypbin-system/src/main/java/cn/ypbin/admin/system/service/support/AbstractDataScopeResolver.java

# 数据范围相关用例
mvn -o -B -Dypbin-starter.version=4.1.0-SNAPSHOT -pl ypbin-service/ypbin-system -am test \
  -Dtest='AdminDataScopeHandlerTest,DataScopeMigrationBoundaryTest,SysUserServiceImplDeptScopeTest'
```
