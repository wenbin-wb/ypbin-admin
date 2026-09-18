# 注解鉴权（`@SaCheckPermission`）灰度开启手册

> 适用分支：`main`（微服务版）。本手册只讲**怎么开、开之前必须先做什么、怎么回滚**；
> 权限码维护规则（菜单/注解/前端三向核对）见 `.claude/skills/ypbin-admin-dev/SKILL.md`。

## 1. 一句话结论

本仓 156 处 `@SaCheckPermission`（29 个 Controller、92 个去重权限码）在**开启前一律是惰性的**：
既不报错也不拦截。开启方式是**在该服务自己的 Nacos dataId 里把 `ypbin.security.interceptor` 置为 `true`**；
回滚就是置回 `false`（改代码、回滚应用版本都不需要）。

## 2. 生效的三层条件与现状

| 层 | 条件 | 本仓现状（源码级核实） |
|---|---|---|
| ① 拦截器装配 | `SecurityAutoConfiguration#saTokenWebConfigurer`（`@ConditionalOnProperty(ypbin.security.interceptor=true)`，starter 默认 `true`）注册 `SaInterceptor` | **system**：`deploy/nacos/ypbin-system.yaml:83` 显式 `false`；**ai**：`deploy/nacos/ypbin-ai.yaml:22` 显式 `false`；**auth**：dataId 里**没有该键** ⇒ 走默认 `true`，`excludes` 只列了登录类路径（`ypbin-auth.yaml:10-19`）⇒ **auth 的拦截器与注解鉴权其实已经在生效** |
| ② 权限数据源 | 宿主提供 `PermissionProvider`；未提供时框架装配「返回空列表」的默认实现 ⇒ 注解**恒不通过** | 本轮补齐三处：system `provider/SystemPermissionProvider`、ai `core/AiPermissionProvider`、auth `support/AuthPermissionProvider` |
| ③ 登录态可校验 | 本服务能用自己的 Sa-Token DAO 读出 auth/网关写入的会话 | system/ai 已有 Redis DAO，但**缺序列化插件**，见 4.2 |

**重要纠正**：`SaInterceptor.preHandle` 会先执行 `SaAnnotationStrategy.checkMethodAnnotation(method)` 再做
`StpUtil.checkLogin()`（对 `sa-token-spring-boot-webmvc-v3v4-common:1.46.0` 的字节码核实：`isAnnotation`
默认 `true`，`SaTokenWebConfigurer` 未调用 `isAnnotation(false)`；`includes` 默认 `/**`）。
⇒ **在 auth 上新加 `@SaCheckPermission` 会立即生效，不受任何开关保护**；只有 system/ai 因为显式配了
`interceptor: false`，新注解才是惰性的。

权限码唯一来源：**`sys_role_menu` → `sys_menu.auth_code`**（本仓**没有** `sys_permission` 表）。
平台超管在 `SysPermissionServiceImpl#listPermissions` 第一行短路返回 `*:*:*`
（`AdminConstants.ALL_PERMISSION`）⇒ 超管**不会**因为「菜单没勾满」掉权限。

ai/auth 无该库表，经 `SysCache`（键 `sys:perm:user:<id>` / `sys:role:user:<id>`，命中原样返回、
未命中回源 Feign 并回填）复用 system 的唯一一份判定。**fail-closed**：system 不可达时
`SysCache` 抛业务异常、Provider 不捕获，请求以 `R.code=409`「系统服务暂不可用」失败
——既不放行，也不伪装成「无权限 403」。Feign 超时已显式钉死
（`deploy/nacos/ypbin-common.yaml:35-41`：连接 2000ms / 读取 5000ms）。

## 3. 一键回滚

把该服务的 `ypbin.security.interceptor` 置回 `false` → 发布 → **重启**。
`@ConditionalOnProperty` 不满足 ⇒ `SaTokenWebConfigurer` 整个 Bean 不装配 ⇒ 拦截器与注解校验一起失效。
Bean 条件在**启动期**求值，配置热更新不生效，必须重启。
（对 auth 而言回滚 = 显式补上 `interceptor: false`，因为它当前是「默认开」的状态。）

## 4. 开启前必须满足的前置条件

### 4.1 先给租户角色勾好菜单（否则一开就是「功能全黑」）

种子数据里 `sys_role_menu` **只给平台超管（role_id=1）** 插了行：

- `deploy/sql/002-data.sql:369-371`：`sys_template_menu` 只把 `platform_only=0` 的菜单挂到模板 1；
- `deploy/sql/002-data.sql:373-374`：`INSERT INTO sys_role_menu SELECT 1, id FROM sys_menu WHERE platform_only = 1`
  ⇒ **租户角色（2/3）在 `sys_role_menu` 一行都没有**。

而 `SysPermissionServiceImpl#listPermissions` 对租户用户走 `menuMapper.selectByUserId(userId)`
（用户→角色→菜单）⇒ 权限码集合为空 ⇒ 开启后**所有 `@SaCheckPermission` 接口对租户用户一律 403**，
看起来就像「代码坏了」，实际是数据没配。

**动作**：用平台超管登录 → 角色管理 → 给每个租户角色勾选所需菜单（或直接补 `sys_role_menu` 行）。
验收：该角色用户的 `listPermissions` 非空。

### 4.2 system 那条 `interceptor: false` 的注释已部分过期 —— 但仍不要第二个开 system

`deploy/nacos/ypbin-system.yaml:81-83` 的理由是「关闭本地 Sa-Token 全局拦截，避免 system 直读 Redis 会话
导致 token 无效」。核实结论分两半：

- **「system 没有 Redis DAO」这个前提已不成立**：`ypbin-common/pom.xml:18-25` 已把
  `sa-token-redis-template` 加进**所有服务**的公共依赖（注释本身写明是为修「system 在线用户列表恒为空」），
  system 与 auth/网关现在共用同一套 Redis 会话存储。
- **结论已由实测更正**：`sa-token-jackson3` **并不是 system/ai 缺失的依赖** ——
  `mvn -pl ypbin-service/ypbin-system dependency:tree -Dincludes=cn.dev33:sa-token-jackson3` 实测输出
  `cn.dev33:sa-token-jackson3:jar:1.46.0:compile`，它是经 `sa-token-spring-boot4-starter` **传递**进来的
  （与"读 Token-Session 时运行的正是 `SaJsonTemplateForJackson3`"这一现场证据吻合）。
  ⇒ 原先"缺序列化插件 ⇒ 打开拦截器可能全站 401"的推断**不成立**，无需补依赖。
- **但有一条更要紧的规则**：凡**写入 Session 的对象**都必须登记进
  `META-INF/satoken/sa-json-type.list`（Sa-Token 用 `@class` 多态白名单校验，未登记则**读**会话时抛
  `InvalidTypeIdException`）。本轮"在线用户终端字段全空"即因此（`OnlineUserHelper$Terminal` 漏登记，已修）。
  ⚠️ 新增会话对象时务必同步登记。

**动作**：**先在 auth 上验证登录校验链路**（auth 本就开着拦截器，属于既有事实），确认「登录 → 携带 token
访问 auth 受保护接口」正常，再动 system。若在 system 上开启后出现 `NotImplException` 或「登录状态已过期」，
说明序列化插件是必需的：给 system/ai 补 `cn.dev33:sa-token-jackson3:${sa-token.version}`（与网关/auth 对齐）后再开。

### 4.3 待决策：`/social/bind|unbind` 是否补权限注解（本 PR **未**添加）

`SocialAuthController` 的 `POST /social/bind/{source}`、`POST /social/unbind/{source}` 目前**无权限注解**。
影响面分析建议补 `@SaCheckPermission`，但按上面 §2 的纠正，**在 auth 上补注解不是「零影响」**：

1. auth 的拦截器已在生效（默认 `true`）⇒ 注解会**立即**开始鉴权；
2. `system:social:bind` / `system:social:unbind` 在 `sys_menu.auth_code` **没有对应行**（全仓 SQL 与
   92 个权限码清单里都没有 `social:*`）⇒ 无法在界面上勾选授权；
3. 两条路径又不在 `ypbin-auth.yaml` 的 `excludes` 里。

⇒ 直接补注解的后果是：**部署即让所有非超管用户无法绑定/解绑第三方账号（403 toast）**，与「本 PR 对现网零影响」
冲突。因此本轮**没有**加这两个注解，改为给出安全的落地顺序：

**步骤（顺序不能反）**

1. 先补数据（示例，菜单 ID 与 i18n `title` 需与前端条目对齐后再执行）：
   ```sql
   INSERT INTO sys_menu (id, pid, name, type, platform_only, auth_code, title, sort, create_time, status, is_deleted)
   VALUES (5072, 0, 'SocialBind',   'button', 0, 'system:social:bind',   'page.profile.social.bind',   1, NOW(), 1, 0),
          (5073, 0, 'SocialUnbind', 'button', 0, 'system:social:unbind', 'page.profile.social.unbind', 2, NOW(), 1, 0);
   INSERT INTO sys_template_menu (template_id, menu_id) VALUES (1, 5072), (1, 5073);
   -- 再到「角色管理」给租户角色勾上（或直接 INSERT INTO sys_role_menu）
   ```
2. 确认目标角色已拿到这两个权限码（用超管以外的账号验证 `listPermissions` 含它们）之后，再加注解：
   ```java
   @SaCheckPermission("system:social:bind")    // bind()
   @SaCheckPermission("system:social:unbind")  // unbind()
   ```
3. **另一条同样正当的路**：不加注解，维持「仅需登录」。这与本仓对自作用域接口的既有约定一致
   ——`UserProfileController` 类注释写的是「操作对象恒为当前登录用户，仅需登录、不挂用户管理权限」，
   其头像上传/改资料/改密码三个写操作都没有权限注解。

两条路都可用，需明确选一条并在评审记录里写清理由。

## 5. 分步开启（顺序：auth → system → ai）

每一步都做完「改配置 → 发布 → **重启服务** → 跑验收 → 观察 → 再下一步」，不要一次全开。

| 步骤 | 服务 | 改动 | 验收重点 |
|---|---|---|---|
| 1 | auth | 无需改开关（已默认开启）。本步只做**验证**：确认登录链路与登录态校验正常 | `/login`、`/sms/**`、`/social/authorize/**`、`/social/callback/**`、`/social/platforms`、`/captcha` 仍在 `excludes`（`ypbin-auth.yaml:10-19`）且免登录；带 token 访问 `/social/bindings` 正常；无 token 返回 `R.code=401`。若这一步就异常，**不要**继续开 system |
| 2 | system | `deploy/nacos/ypbin-system.yaml:83` 由 `false` 改 `true` | 先按 4.1 勾好菜单；`/internal/**`、`/open/**`、`/open-api/**`、`/actuator/**` 已在 `excludes`（:84-88），内部 Feign 调用不能被误拦；平台超管全页面零报错 |
| 3 | ai | `deploy/nacos/ypbin-ai.yaml:22` 由 `false` 改 `true` | `ai:*` 权限码已在种子数据里（含 `platform_only=1` 的模型/用量），重点看租户用户能用哪些、超管全通 |

## 6. 三个角色的验收清单

**A. 平台超管（role_id=1）——要求零报错**

1. 登录后遍历自己可见的全部菜单/页面各操作一次，**不得出现 `R.code=403`（无权限）或 409（系统服务暂不可用）**；
2. 直接确认权限码：`listPermissions(超管 userId)` 返回 `["*:*:*"]`
   （回归测试 `SysPermissionServiceImplTest#superAdminShouldShortCircuitWithAllPermission`）；
3. 对 `sys_menu` 里没有 `auth_code` 行的接口（如 4.3 里尚未建行的 `system:social:*`）超管**也应可访问**
   ——这是 `*:*:*` 通配真正生效的判据。

**B. 租户管理员**

1. 已勾选菜单对应接口 200；未勾选返回 `R.code=403`（HTTP 状态仍是 200）；
2. **改角色勾选后即时生效**：在「角色管理」里增/删一个菜单 → 同一浏览器会话、**不重新登录**，立刻访问该菜单
   对应接口，应立刻放行/拒绝。判据是 `sys:perm:user:<id>` 被清（回归测试
   `SysRoleServiceImplPermissionCacheTest#updateRoleShouldEvictAllRoleUsersPermissionCache`）；
3. 角色被禁用后该角色用户立即掉权限（`updateStatus` 同样清缓存）。

**C. 普通租户用户**

1. 只读/自助接口 200（如个人中心 `/user/profile`）；管理类写接口 403；
2. **前端对权限失败只弹 toast、不跳 403 页**：后端按本仓「统一 HTTP 200」约定返回 `R.code=403` +
   文案「没有访问权限」（`SaTokenExceptionHandler` 无 `@ResponseStatus`），前端
   `apps/web-antd/src/api/request.ts:196-202` 只做 `message.error(...)`（不跳转；403 页面只在路由守卫里用到）
   ⇒ 验收时看到的是**右上角一条错误提示**，不是整页 403。

**D. 回滚演练（至少做一次）**

在 system 或 ai 上把 `interceptor` 置回 `false` → 发布 → 重启 → 用 B 里「被拒绝的那个接口」再访问：
应恢复放行。这既证明回滚路径有效，也确认「403 是拦截器带来的」。

## 7. 观察项与已知风险

1. **鉴权依赖 system 可用性**：ai/auth 的权限码经 `SysCache`→Feign（读超时 5s）取；system 故障时业务接口
   返回 409 而不是放行（刻意 fail-closed），代价是 system 成为鉴权链路单点，容量评估要按此口径。
2. **权限缓存永久有效**：`SysCache` 的用户/角色缓存 TTL 传 `null`，一致性**完全依赖写路径主动失效**。
   已核实存在的失效点：角色授权/状态/删除（`SysRoleServiceImpl`）、菜单增改删（`SysMenuServiceImpl`）、
   租户权限模板（`SysAuthTemplateServiceImpl`）、租户变更（`SysTenantServiceImpl`）、用户角色分配与用户增改删
   （`SysUserServiceImpl`）。**残留竞态**：失效发生在事务提交**之前**，极端并发下读侧可能把变更前的权限
   回填进永久缓存；要根治需改为「提交后失效」（`TransactionSynchronization` /
   `@TransactionalEventListener(AFTER_COMMIT)`），本轮未做。
3. **`*:*:*` 只在 `SysPermissionServiceImpl` 出现一处**：任何在 `PermissionProvider` 里过滤结果的改动都会让
   超管掉权限；三处 Provider 均为纯透传并有测试锁定。
4. 关闭 `interceptor` 后注解不生效，但不代表没有其它防线：网关仍做登录校验，`/internal/**` 有内部凭证守卫，
   写操作有 `@Idempotent`/`@Log`。

## 8. 证据边界（本机未核实项）

- 本机无 DB/Redis、未启动任何服务，**所有「运行期行为」结论均未实测**，仅源码级/单测级证据：
  system 的会话序列化兼容性（4.2）、403 文案与 HTTP 状态、菜单勾选后的实际放行效果。
- 「156 处注解 / 29 Controller / 92 去重权限码 / auth 与网关 0 处」为源码扫描结果（本机实测一致），非运行时统计。
- 4.3 的建行 SQL 是**示例**，执行前需确认菜单 ID 不冲突并补前端 i18n 文案。
