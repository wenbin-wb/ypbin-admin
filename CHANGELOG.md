# 更新日志

本项目遵循[语义化版本](https://semver.org/lang/zh-CN/)：`主版本.次版本.修订号`。

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [未发布]

### 新增

- **埋点事件目录分层：新增 admin 的 project 层目录**（`ypbin-common/src/main/resources/META-INF/ypbin/tracking-events.json`）。
  承接从 starter 的 base 目录**迁出**的业务示例事件 `system.user.export`（starter 侧同批把它从 `docs/tracking-events.json` 移除）。
  运行时仍是「base + 宿主项目目录合并」：starter 的 `TrackingCatalogLoader` 用 `classpath*:` 取回类路径上
  **全部**同名资源、按所在归档区分两层后合并，同一事件码以 project 为准并在启动时以 WARN 逐字段打印覆盖差异
  （`TrackingEventCatalog#overriddenCodes()` 另给出可编程的覆盖标注）。
  - **为什么放 `ypbin-common` 而不是 `ypbin-system`**：目录必须落在**记录事件的那个服务**的类路径上——
    `TrackRecorder` 在录制入口就按目录校验事件码，而 auth 与 system 都会录制（`ypbin-common` 是两者共同的宿主 jar）。
    只放 `ypbin-system` 会让 auth 侧录制 admin 自有事件时被判「未登记」。
  - **事件码集合不变**：迁移前后「base ∪ project」的**联合视图逐码等价**（迁移前 base 10 码；
    迁移后 base 9 码 + project 1 码，并集仍是同样的 10 码，description / properties / type / maxLength 逐字段一致）。
    故前端生成物 `events.generated.ts` **零改动**、漂移门禁保持绿。
  - **该码目前无发送方**（全仓无 `system.user.export` 的上报点，也无按码分支的消费方，只有事件目录页的只读展示）——
    属**死事件**，本次按既定决策只做「迁位」不做删除；删码是破坏性动作，需单独决策。
- **架构门禁新增规则：`service/impl` 禁止依赖 `provider`**（`ypbin-architecture-tests` 的 `CodingRulesTest`）。
  `provider` 是宿主按 starter 端口契约给出的适配实现层、`service/impl` 是业务实现层，实现层直连适配实现类会把依赖方向
  倒置成硬耦合。这是一次**真实回归**的护栏：`SysUserServiceImpl` 曾直接持有 `provider.AdminDataScopeHandler` 复用
  「该部门是否在数据范围内」的判定，上一轮重构已抽为 `service/support` 的共享能力（`DataScopeResolver`），本规则把
  「不许倒回去」固化成构建失败。规则带**有效性自检**：合成违规（`service/impl` 持有真实 `provider.AdminDataScopeHandler`）
  必须转红，同时断言**反向依赖（provider → service/impl）与无关依赖不被误报**（证明方向性正确、非恒真也非恒假）。

- **AI 用量落库：宿主侧 `AiUsageListener` 实现（`ypbin-ai` 的 `AdminAiUsageListener`）**。
  此前 `ai_usage_log` **没有任何写入方**（全仓只有 Mapper 与统计读取），用量看板恒为空。
  starter 这一批补齐了回调触发点并改了契约（`AiUsageInfo` 的 token 由 `long` 改为可空 `Long`，
  新增 `outcome` 与 `errorMessage`），本仓按新契约实现宿主侧监听器：
  - **用户/租户维度由宿主补齐**（starter 回调不携带用户信息）：先取同线程
    `TenantContext`/`IdentityContext`，取不到时用 `conversationId`（即 `ai_chat_session` 主键）
    精确查一行补齐 tenantId/userId；匿名入口（`share-<kbId>`/`widget-<kbId>`/`kb-query-<kbId>`）
    没有用户与会话实体，`user_id`/`conversation_id` **如实落 NULL**，不伪造 ID；
    租户实在取不到则记 error 并放弃本次落库（禁止伪造成默认租户）。
  - **`ai_usage_log` 最小增量**（`deploy/sql/003-ai-schema.sql` + `deploy/sql/migration/2026-09-17-ai-usage-log-outcome.sql`）：
    `user_id` 与三个 token 列改可空（NULL＝上游未回报，**绝不折算成 0**）、新增
    `outcome`（success/failure/cancelled）与 `error_message`。已有库执行 migration 子目录脚本；
    全新安装无需额外操作（003 已是最终结构）。
  - **统计侧 NULL 口径**：聚合一律直写 `SUM(total_tokens)`，不在聚合参数上套 `COALESCE`
    （未知行绝不按 0 参与求和/均值）；每个聚合点额外返回未知条数
    （`unknownTokenCalls`/`unknownCalls`），用量总览另给出三类终局结果计数
    （`successCalls`/`failureCalls`/`cancelledCalls`）。
  - **异常隔离**：落库异常在监听器内捕获并 `log.error(..., ex)` 记完整堆栈与业务标识，
    不向对话主流程抛出；单条 INSERT（`AiUsageLogWriter`，`@Transactional(rollbackFor = Exception.class)`），
    无循环查询、无批量。
  - 测试：`AdminAiUsageListenerTest`（成功/失败/取消各自落库；token 为 null **不得写成 0**；
    落库失败不影响主流程且带堆栈留痕；会话补齐/同线程上下文/未知租户三种解析路径；超长原因按列宽截断）。
- **starter 依赖版本升至 `3.4.0`**（`pom.xml`）。新契约（可空 token + `AiUsageOutcome`）
  只存在于 starter 未发布的 3.4.0 线（3.3.0 的 `ypbin-starter-ai` 只有 `AiUsageInfo`/`AiUsageListener`
  两个类）。⚠️ **合并前须等 starter 3.4.0 正式发布**：本仓 CI 有「starter 版本必须等于最新 GitHub Release」
  校验，SNAPSHOT 会直接失败；发布后由 `sync-starter-version` 工作流改写为发布版号。
  本地开发需先 `mvn -Dmaven.test.skip=true install` 安装 starter 3.4.0。
- **数据范围处理器 `AdminDataScopeHandler`（starter 端口 `DataScopeHandler` 的宿主实现）**。
  此前宿主未实现该端口（实现数为 0），9 处 `@DataPermission` 全为空转——查询与写操作的 UPDATE/DELETE
  都不加任何数据范围条件。现按 `sys_role.data_scope` 计算 SQL 片段：平台超管（`PLATFORM` +
  `PLATFORM_SUPER` 角色）不受限；否则取各角色数据范围并集（1 全部⇒不加条件、2 本部门及以下⇒本部门+
  部门树后代、3 本部门、4 仅本人⇒`id = 当前用户`、5 自定义⇒角色绑定部门+其后代），部门条件与本人
  条件以 `OR` 组合；取不到身份头/无有效角色/解析结果为空时**拒绝全部**（`id = -1`）并记日志，
  不放行全量。只治理 `sys_user` 表（其余表无 `dept_id` 列，返回 null 放行）；租户隔离仍由 tenant
  拦截器独立施加，不拼 `tenant_id`；角色-部门关联走批量 IN（判空短路），解析自身的查询用线程内标记防递归。
- **敏感词词库提供者 `DbSensitiveWordProvider`（starter 端口 `SensitiveWordProvider` 的宿主实现）**。
  词库取自系统参数 `sys_config.SENSITIVE_WORDS`（逗号/分号/换行分隔），复用既有参数表与「系统参数」
  维护界面，不新建词表；词库为空时本类自行记 WARN（starter 只在「无 Provider 且配置项为空」时告警，
  宿主提供 Provider 后该告警不再触发，必须由实现把「过滤等同无操作」说出来）；读取失败带堆栈抛错，
  绝不降级为空词库。种子数据已补该参数行（`deploy/sql/002-data.sql`，默认留空待填）。
  **词库在启动期读取一次，改动后需重启生效**（starter 的契约如此，`SensitiveWordService#reload`
  本仓尚未接入）。
- **权限数据源（starter 端口 `PermissionProvider`）三处宿主实现，注解鉴权具备开启条件**
  （`ypbin-system` / `ypbin-ai` / `ypbin-auth`）。此前宿主均未实现该端口，框架装配的是「返回空列表」的
  （`ypbin-system` / `ypbin-ai` / `ypbin-auth`）。此前宿主均未实现该端口，框架装配的是「返回空列表」的
  默认实现，156 处 `@SaCheckPermission`（29 个 Controller、92 个去重权限码）即便拦截器打开也恒不通过。
  - `SystemPermissionProvider`：复用本地 `SysPermissionService`（权限码唯一来源 `sys_role_menu` →
    `sys_menu.auth_code`），**原样透传**平台超管的 `*:*:*` 短路结果，不做任何过滤/截断
    ——这是「超管不掉权限」的唯一保障，已由 `SysPermissionServiceImplTest` 与
    `SystemPermissionProviderTest` 双向锁定。
  - `AiPermissionProvider` / `AuthPermissionProvider`：本服务无该库表，经 `SysCache`
    （`sys:perm:user:<id>` 永久缓存，未命中回源 Feign）复用 system 的唯一一份判定，避免每请求一次 RPC。
    **fail-closed**：system 不可达时 `SysCache` 抛业务异常、Provider 不捕获 ⇒ 请求以
    `R.code=409`「系统服务暂不可用」失败——既不放行，也不把依赖故障伪装成「无权限 403」；
    响应成功但数据缺失、loginId 缺失/非法时一律返回空集合（拒绝）。Feign 超时沿用共享配置的
    显式钉死值（`deploy/nacos/ypbin-common.yaml:35-41`：连接 2000ms / 读取 5000ms）。

### 修复

- **`auth` 不再传递依赖 `ypbin-starter-data`（MyBatis-Plus）——把 SKILL 里「auth 不直连共享库」从口头约定变成编译期门禁**
  （`TRACKING-TAILS.md` 的既有偏差 #15）。根因不在 auth 自己的 pom，而在**跨服务契约把持久化实体当 DTO 用**：
  `ISystemClient`/`SysCache` 直接返回 `SysUser`/`SysUserSocial`，而两者继承 `BaseEntity`，
  于是无数据源的 auth 被迫跟着依赖 starter-data（连 Mockito 为 `ISystemClient` 生成 mock 都会因签名里的实体
  无法加载而失败——本轮的编译期实验正是这样暴露出来的）。
  - **契约收窄为只读视图**：新增 `SysUserDto`/`SysUserSocialDto`（字段与实体**逐一同名同类型**，禁改名映射；
    视图**结构上不含 `password`/`accessToken`**），`ISystemClient` 的 6 个方法
    （`getUserByUsername`/`getUserById`/`getUserByPhone`/`searchUsers`/`getSocialBinding`/`listSocialBindings`）
    与 `ISystemClientFallback`/`SystemClientImpl` 同步改签；实体→视图的投影集中在
    `ypbin-system` 的 `feign/support/UserViewConverter` 一处（**刻意不放 api 模块**：它必须引用实体，
    置于 api 会让已排除 `starter-data` 的 auth 拿到一个「能加载、一解析方法就 `NoClassDefFoundError`」的类）。
  - **auth 的 pom 显式排除 `ypbin-starter-data`**：一旦有人在 auth 里再引用实体类型，编译期即因缺类失败——
    这条约定从此由构建强制。`ai` **不加**该排除：它有自己独立的 `ai_*` 表与 Mapper，
    「不直连共享库」指的是不访问 system 的表，不是不许用 MyBatis。
  - **缓存 key 升版到 v2**（`sys:user:v2:*` / `sys:social:v2:*`）：用户/绑定快照是**永久缓存**，
    而 `CacheService#getOrLoad` 对命中值是「无类型校验的强转」——载荷由实体收窄为视图后若沿用旧 key，
    升级后会读到旧实体对象并在强转处抛 `ClassCastException`（登录直接不可用）。5 处 `@CacheEvict`
    注解已同步升版；旧的 v1 键不再被读取。
    **运维（可选）**：v1 用户/绑定键是永久键，如需释放内存可执行
    `DEL sys:user:username:* sys:user:id:* sys:user:phone:* sys:social:binding:* sys:social:bindings:*`
    （不执行也不影响正确性：新代码只读 v2 键）。
  - **新增门禁**：`SourceConventionTest` 增加「`@CacheEvict` 的 key 必须存在于 `SysCache` 的 key 常量中」，
    并带**规则有效性自检**与**变异验证**（把一处失效 key 改回 v1 → 精确转红；回滚 → 绿）。
    这类漂移此前完全静默：失效打在不再被读取的键上，改状态/改角色后登录仍用旧快照。
  - **验证**：受影响四模块 `mvn test` 全绿（`ypbin-system-api` 12 / `ypbin-common` 17 / `ypbin-auth` 30 /
    `ypbin-system` 159 / `ypbin-ai` 21），架构测试模块 35 项全绿（含新规则）。
- **用户名查重移出数据范围，跨部门重名改为友好业务错误**（`SysUserServiceImpl` / `UserAccountSupport` / `SysUserMapper`）：改用**语句级** `@InterceptorIgnore(dataPermission = "true")` 的全局计数语句（`countByUsernameGlobal`），不再受 `@DataPermission` 部门条件影响。

  `SysUserMapper`）。`uk_username` 是**不带 `tenant_id` 的全局唯一键**，而 `updateUser` 带
  `@DataPermission`（数据范围按部门过滤），原先的 `exists()` 查重落在该范围内 ⇒ **跨部门重名查不到** ⇒
  校验通过后由数据库唯一键抛原始 SQL 错误。现改走语句级跳过数据权限的
  `SysUserMapper#countByUsernameGlobal`（`@InterceptorIgnore(dataPermission = "true")`，已核实
  MyBatis-Plus 3.5.17 `DataPermissionInterceptor#beforeQuery/beforePrepare` 首行即判该标记）
  + `TenantContext.executeIgnore`（关租户过滤），两道过滤缺一不可：
  `TenantContext` 关不掉数据权限（`DataPermissionContext` 只有进入/退出、无挂起语义，
  外层已激活时内层 `@DataPermission(ignore = true)` 并不生效），故必须用语句级注解。
  测试：`UserAccountSupportTest`（同租户内跨部门重名同样报「用户名已存在」、执行时断言租户过滤已关且退出后复位、
  编辑排除自身）、`SysUserMapperInterceptorIgnoreTest`（走真实 Mapper 解析路径断言 MP 判据为真，
  并以未标注语句/不存在语句反向证明判据非永真）。
- **操作日志 `sys_log.clientId/clientType/authType` 不再恒为空**：新增 `SessionLogClientProvider`
  （`ypbin-common`，由 `RemoteLogAutoConfiguration` 装配，带 `@ConditionalOnMissingBean` 可被宿主覆盖），
  从登录会话中的 `LoginUser` 读这三个值——gateway 身份头只有 id/username/tenantId/deptId/roles，
  **不新增任何请求头契约**，而是复用 auth 登录时写入、三服务共享同一 Redis 会话存储的登录态。
  读会话失败时记 error 且让本条日志照常落库（仅三列为空），不因三个附加字段丢掉整条审计记录。
- **在线用户接口改为统一分页响应**：`GET /online-user/list` 由 `R<List<OnlineUserResp>>` 改为
  `R<PageResult<OnlineUserResp>>`（新增 `OnlineUserQuery extends PageQuery`，`keyword` 语义不变）。
  在线用户来自会话存储而非数据库，属**内存分页**：先枚举全部在线会话再切片，分页只减少传输量，
  不减少会话读取开销（O(在线会话数)）；页码越界时 `items` 为空而 `total` 仍为真实总数；
  非法页码/每页条数显式报错，不做静默纠正。
- **角色授权变更时的权限缓存清理由「逐用户一次缓存往返」改为批量一次删除**
  （`ypbin-system-api`：`SysCache.evictUserAuth(Collection<Long>)`）。用户权限缓存是永久缓存
  （TTL 传 `null`），一致性完全依赖写路径主动失效；受影响用户多时原实现按用户逐个 `DEL`，
  放大为 N 次缓存往返。现合并键后一次删除，空集合短路、`null` 元素跳过；
  `SysRoleServiceImpl`、`SysMenuServiceImpl` 的失效入口改用批量语义（仍为**精确失效**，
  只清受影响用户的 `sys:role:user:*` / `sys:perm:user:*`，不做整体清）。
  新增回归测试：`SysRoleServiceImplPermissionCacheTest`（改角色勾选菜单/改角色状态后必须清掉该角色下
  **全部**去重用户的缓存；角色下无人时不得发起删除）、`SysCacheTest` 的批量与短路用例。
- **内部端点 `GET /internal/user-by-id` 补齐租户忽略，第三方登录不再必然失败**
  （`SystemClientImpl` / `SysUserService` / `SysUserServiceImpl`）：`/auth/social/callback/**` 在网关
  白名单内、匿名链路没有网关签发的 `X-Tenant-Id`（`SaTokenGatewayAuthProvider` 仅在登录后签发），
  而 `sys_user` **不在** `ypbin.tenant.ignore-tables` 且 `fail-on-missing-tenant=true`（fail-closed）
  ⇒ 该端点原先调用继承自 `IService` 的 `getById`，必然抛「缺少租户上下文」，**第三方回调登录直接失败**。
  现新增 `SysUserService#getByIdGlobal`（内部 `TenantContext.executeIgnore`，与同链路 6 个兄弟方法
  `getByUsername`/`getByPhone`/`verifyPassword`/`countUsers`/`searchUsers`/`updateLastLoginTime` 同口径）
  并由端点委派；**刻意不覆写 `getById`**，否则所有既有调用者会悄然失去租户隔离。
  同文件其余端点已逐一核对：其余 6 个走已在 service 内忽略租户的方法，`sys_config`/`sys_log`/
  `sys_user_social`/`sys_track_event*` 相关端点命中的都是 `ignore-tables` 表，**无第二处漏项**。
  测试：`SystemClientImplUserByIdTest`（必须委派 `getByIdGlobal`，并反向禁止走 `getById`）、
  `SysUserServiceImplTenantIgnoreTest`（用真实 `DefaultTenantLineHandler` + 生产同口径配置断言
  「无上下文时 `getById` 必被拦、`getByIdGlobal` 必放行、退出后不泄漏」）。
- **手机号查重移出数据范围，跨部门重号改为友好业务错误**（`SysUserMapper` / `UserAccountSupport`）：
  与用户名查重同源——`updateUser` 带 `@DataPermission`，原先的 `exists()` 查重落在部门范围内 ⇒
  **跨部门重号查不到** ⇒ 校验通过后由唯一键抛原始 SQL 错误。现新增
  `SysUserMapper#countByPhoneGlobal`（语句级 `@InterceptorIgnore(dataPermission = "true")`）
  + `TenantContext.executeIgnore`（关租户过滤）并改用它，两道过滤缺一不可。
  测试：`UserAccountSupportTest` 补 6 个用例（同部门/跨部门重号均报「手机号已存在」、执行时断言租户过滤已关
  且退出后复位、编辑排除自身、手机号为空不查库、查重失败不掩盖异常）、
  `SysUserMapperInterceptorIgnoreTest` 补该语句命中 MP `willIgnoreDataPermission` 的断言。
- **数据范围报错不再教用户用错的机制**（`AdminDataScopeHandler`）：原文案建议
  「调用侧改用 `@DataPermission(ignore = true)`」，但该机制在**外层已激活数据权限时不生效**——
  `DataPermissionContext` 只有 `enter/exit/isActive`、**无挂起语义**，`DataPermissionAspect` 命中
  `ignore=true` 时只是自己不再 `enter()` 而直接 `point.proceed()`，处理器依旧会被回调，用户照做后问题仍在。
  现改为指向真正可行的机制（语句级 `@InterceptorIgnore(dataPermission = "true")` / 独立 Mapper 语句 /
  不经 Mapper 的通道），并把「为什么无效」及三条可行路径写进注释，避免后来者重踩。
  测试：`AdminDataScopeHandlerTest` 捕获日志文本，断言文案包含可行机制且不再出现旧文案。
- **在线用户姓名回填改为跨租户全局**（`SysUserServiceImpl#pageOnlineUsers`）：姓名批量回填原先直接走
  基类 `listByIds`，会被租户行拦截器追加 `tenant_id` 条件（`sys_user` 不在 `ignore-tables`）⇒
  平台管理员**只能回填到本租户姓名，他租户行 `realName` 静默为 null**。而在线会话本身是跨租户的
  （该接口挂在 `OnlineUserController` 的 `@PlatformAccess` 平台级语义下），故改为
  `TenantContext.executeIgnore(() -> listByIds(ids))`。此处**无需** `@InterceptorIgnore`：数据权限只在
  `@DataPermission` 作用域内才拼条件，本方法未标注、不产生额外查询。
  测试：`SysUserServiceImplOnlineUserTest` 断言他租户在线用户同样回填到姓名、查询时租户过滤已关且退出后复位、
  异常路径不吞异常也不泄漏忽略状态。
- **写路径补 `deptId` 数据范围校验**（`SysUserServiceImpl#createUser/updateUser` +
  `AdminDataScopeHandler#isDeptWithinScope`）：`@DataPermission` 只把范围条件拼进被标注方法内已发出的 SQL，
  而写方法的 `deptId` 是**请求入参**、不经过任何查询（`createUser` 更是完全没有 `@DataPermission`）⇒
  部门范围管理员可把用户建/改到任意部门。现两个写方法**先鉴权再校验/落库**，调用
  `isDeptWithinScope`（与读路径共用 `resolveScope` 与 `RESOLVING` 递归标记，**不新写第二套口径**）：
  平台超管或任一角色「全部数据」⇒ 不限；否则 `deptId` 必须在可见部门集合内（「本部门及以下」/「自定义」
  照常展开部门树后代）。越界、以及 `deptId` 为空（读路径 `dept_id IN (...)` 匹配不到 NULL 行）一律
  抛友好业务错误「目标部门不在你的数据范围内」，不静默忽略、不落到数据库层。
  **代价**：每次写操作新增 2～4 次查询（超管判定 + 角色 + 按需部门树/角色-部门），调用点不在任何循环内。
  **语义变化**：部门范围操作者现在**不能**把用户 `deptId` 置空或改到范围外（原先可静默做到，之后自己再也读不到）。
  测试：`AdminDataScopeHandlerTest`（部门范围可写本部门/拒他部门、超管不受限、部门树后代展开、四类 fail-closed）、
  `SysUserServiceImplDeptScopeTest`（越界必须友好报错且不落库/不查重、在范围内放行继续执行）。

### 文档

- 新增 [`docs/permission-rollout.md`](docs/permission-rollout.md)：`@SaCheckPermission` 灰度开启手册
  ——生效三条件与现状、一键回滚（`ypbin.security.interceptor` 置回 `false` 即整体不装配）、
  开启前两个硬前置（先给租户角色勾菜单；system 的 `interceptor: false` 注释已部分过期，须先在 auth
  验证登录校验链路再动 system）、auth → system → ai 的分步开启表、三个角色的验收清单与回滚演练、
  已知风险（鉴权依赖 system 可用、失效发生在事务提交前的残留竞态、前端 403 只弹 toast 不跳页）。

### 说明（本轮刻意未做的两件事）

- **未改动任何 `ypbin.security.interceptor` 开关**（system/ai 仍为 `false`）⇒ 对现网零行为影响。
- **未给 `/social/bind/{source}`、`/social/unbind/{source}` 加权限注解**。影响面分析建议补注解，但
  核实发现：auth 的 dataId **没有**该开关键 ⇒ 走 starter 默认 `true`，其 `SaInterceptor` 早已装配且
  `preHandle` 会先做方法注解校验（`sa-token-spring-boot-webmvc-v3v4-common:1.46.0` 字节码核实）⇒
  在 auth 上新加注解**立即生效**；而 `system:social:bind` / `system:social:unbind` 在 `sys_menu.auth_code`
  没有对应行、无法在界面授权 ⇒ 直接补注解会让**所有非超管用户部署即无法绑定/解绑第三方账号**，
  与「本 PR 对现网零影响」冲突。安全落地顺序（先补菜单行与授权、再加注解）与「维持仅需登录
  （与 `UserProfileController` 的自作用域既有约定一致）」两条路已写进上述文档，待明确选一条。

### 待决策与未处理（本轮盘点发现，未动手）

- **`dept_id IS NULL` 的用户对「部门范围」操作者不可见**：`dept_id IN (...)` 不匹配 NULL，
  这是数据范围语义的自然结果（与同类框架一致）。是否要 OR 出 `dept_id IS NULL`（放宽可见性）属产品决策。
  写路径已与读路径对齐（部门范围操作者不能把用户 `deptId` 置空，见上「写路径补 `deptId` 数据范围校验」）；
  若日后决定放宽读侧可见性，写侧的拒绝规则需同步评估。
- **`exportUsers` 双重注解**：`SysUserServiceImpl.exportUsers` 与 `UserExcelComponent.exportUsers`
  都标了 `@DataPermission`（跨 Bean 调用，切面各生效一次）。嵌套计数正确、无副作用，可择机去掉一处。
- **敏感词词库热更新未接**：改 `sys_config.SENSITIVE_WORDS` 需重启才生效（starter 装配期只取一次词）。
  若要即时生效，应在配置变更事件里调用 `SensitiveWordService#reload`（需处理 `enabled=false` 时 Bean 缺失）。
- **数据范围解析未加缓存**：每次回调 2～4 次查询（超管判定 + 角色 + 按需部门树/角色-部门）。
  缓存的失效点分散（角色、部门、用户角色变更），遗漏即越权，故本轮优先正确性；降本应作为独立改动评估。
- **在线用户分页仍是内存分页**：在线规模达到万级时应改为在会话侧维护可分页索引。
