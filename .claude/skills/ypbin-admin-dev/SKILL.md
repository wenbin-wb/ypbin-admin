---
name: ypbin-admin-dev
description: ypbin-admin 后端开发与合规审计标准。开发任何 Controller/Service/Entity/DTO/Mapper，或审查已有后端 Java 代码是否合规时使用。强制字段全程同名、禁止字段改名映射、禁止内联全限定类名、禁止静默降级、异常统一 200、类必带 @author wenbin + @since、注释不提前端、admin 只写业务通用能力沉 starter。
---

# ypbin-admin-dev — ypbin-admin 后端标准

面向 `ypbin-admin`（构建在自研 `ypbin-starter` 之上，JDK 21 / Spring Boot 4.1 / Sa-Token）。**两种部署形态，规范不同，先分清在哪个分支工作**：

- **单体版（`boot` 分支）**：模块 `ypbin-admin-system`（业务 + 公共，`cn.ypbin.admin.common` + `cn.ypbin.admin.modules.{ai,auth,job,system}`）、`ypbin-admin-server`（启动 + 装配）。当前用户走 `UserContext`/`LoginHelper`（sa-token 会话）。
- **微服务版（`main` 分支，主推形态）**：模块 `ypbin-common`（公共）、`ypbin-gateway`（网关）、`ypbin-auth`（认证）、`ypbin-service/ypbin-{system,ai,job}`（业务服务）、`ypbin-service-api/ypbin-{system,ai}-api`（Feign 接口 + 共享实体/DTO）。当前用户走 `IdentityContext`（身份头模式）；auth/ai **禁止直连共享库**，一律经 `ISystemClient` Feign 调 system 服务。

两种模式：

- **开发模式**：写新 Controller/Service/Entity/DTO/Mapper 时，严格按下面的配方与铁律落地。
- **审计模式**：审查已有代码时，逐条对照「铁律」与「审计清单」，输出违规项 + 整改建议。

> **微服务版关键差异（写代码前必读）**：
> 1. **身份上下文**：单体用 `UserContext`/`LoginHelper`（sa-token 会话）；微服务用 `IdentityContext.getUserId().orElse(null)`（身份头，网关签发 `X-User-Id` 等头）。**微服务下游严禁 `LoginHelper.getUserId()`**（无 sa-token 会话会抛异常）。
> 2. **Feign 目录规范**：api 模块 `ypbin-system-api/.../api/feign/` 放 `ISystemClient`（`@FeignClient` + fallback）+ `ISystemClientFallback`（降级返回失败 `R` 不吞错）；service 模块 `ypbin-system/.../feign/` 放 `SystemClientImpl`（`@RestController implements ISystemClient`）。**新增跨服务数据访问，先扩展 `ISystemClient` + `SystemClientImpl`，禁止在调用方服务加 Mapper 直连**。
> 3. **auth/ai 不直连共享库**：用户/权限/社交绑定等数据一律 Feign 调 system；auth 不依赖 `ypbin-starter-data`。高基查询经 `SysCache`（api 模块缓存类）。
> 4. **`@PlatformAccess` 来自 starter**：`cn.ypbin.starter.security.platform.PlatformAccess`（微服务版），单体版是 `cn.ypbin.admin.modules.system.annotation.PlatformAccess`。平台用户判定实现 starter 的 `PlatformUserChecker` SPI。

作者署名统一 `wenbin`。改动只编译验证不启动服务（见项目 memory `ypbin-admin-workflow`）。

> **skill 内的 starter 引用说明**：本文提到的 `BaseController`/`BaseServiceImpl`/`GlobalExceptionHandler`/`BaseEntity`/`TenantBaseEntity`/`R`/`PageResult`/`PageQuery`/`BusinessException`/`@RefText`/`@Log` 等都在**另一个仓库 `ypbin-starter`** 里，不在 admin 内。文中用类名 + 所属 starter 模块标注（如 `ypbin-starter-extension-crud`），需要看源码时去 `ypbin-starter` 对应模块找，不做跨仓库文件链接（单独打开 admin 时点不到）。

## 铁律（RED — 违反即判不合格，必须整改）

这些是项目硬约束，**全部不可豁免**。它们来自用户明确表态（见各条关联的 memory），不是可商量的风格偏好。

1. **字段全程同名，禁止任何字段改名映射**（memory `ypbin-no-field-mapping`）。DB 列（下划线）= 实体字段（驼峰）= DTO 字段 = 接口 JSON，全链路同一个名字。
   - MyBatis `map-underscore-to-camel-case` 的下划线↔驼峰**不算映射**（标准命名转换，允许）。
   - **禁止使用 `@Mapping(source = "...", target = "...")` 纠正命名分歧**：若字段名不一致，必须直接修改 Java 字段名以对齐数据库列名，绝不留映射胶水代码。
   - 禁止换名字：`nickname→realName`、`parentId→pid` 这种换名字。要对齐某个字段名，就直接把 DB 列建成那个名字（要 `pid` 就建 `pid` 列），不要留旧列名再在 service/DTO 层转。
   - **拼装 ≠ 映射**：多表查出来组装成嵌套对象（如 `RoleResp` 里 `BeanUtils.copyProperties` 后再 `setPermissions(mapper.selectMenuIdsByRoleId(id))`），字段名全程不变，这是结构组装，允许。参照 [SysRoleServiceImpl.java:118](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/service/impl/SysRoleServiceImpl.java) 的 `toRespWithMenus`。

2. **禁止内联全限定类名，一律 import**（memory `ypbin-no-inline-fqn`）。方法签名/变量/返回类型/`new`/静态调用里凡引用其它包的类，先在文件顶部 `import`，正文只写简单类名（`RoleResp`，不是 `cn.ypbin.admin.modules.system.model.resp.RoleResp`）。已有内联 FQN 视为违规。评审自己产出的 Java 专门查这一条。

3. **不做掩盖问题的静默降级，错误要暴露**（memory `ypbin-no-silent-fallback`）。
   - 禁止空 `catch {}` 吞异常、失败静默 `return`、失败回退默认值假装正常、"配置缺失则降级"。
   - 记录异常日志必须携带核心业务标识（如 `userId`/`taskId`/`orderNo`）并传完整异常堆栈（`log.error("处理业务 [{}] 失败: {}", bizId, ex.getMessage(), ex)`），**严禁 `e.printStackTrace()` 与 `System.out.println`**。
   - 可预期的业务错误抛 `BusinessException`（会被全局处理器转成 200 + 业务码，参照 [SysRoleServiceImpl.java:78](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/service/impl/SysRoleServiceImpl.java) `throw new BusinessException("角色不存在")`）。配置缺失该显式报错/warn 日志，不静默。
   - **例外（合理容错，非降级）**：批量操作中单元素失败不阻断整体（如群发通知某用户邮件失败），但**必须记 error 日志**。判据：错误是否被"看见"——看不见的就是坏降级。

4. **每个类的类级 Javadoc 末尾必带 `@author wenbin` + `@since <当天日期>`**（memory `ypbin-code-conventions`）。
   - 用 `@since`，**不用 `@date`**（非标准标签，IDEA 报未知标签黄线）；**不写版本号**（难维护，用户明确不要）。
   - 顶部 Apache-2.0 license 块注释保留（admin 是 `2026-present ypbin-admin authors`，starter 是 `2024-present ypbin-starter authors`，别搞混）。

5. **异常统一 HTTP 200，靠 `R.code` 区分成功/失败**（memory `ypbin-code-conventions`）。Controller 一律返回 `R<T>`：单体版走 `BaseController` 的 `ok()/ok(data)/data(...)`；**微服务版 `BaseController` 已删除**，直接用 `R.ok()` 静态工厂 + `WebRequestUtils`（取请求上下文）+ `IdentityContext`（当前用户）。错误抛异常交给全局 `GlobalExceptionHandler`（starter 的 `ypbin-starter-web` 模块）。**禁止 `ResponseEntity.status(4xx/5xx)`** 或自定义 REST 语义状态码。

6. **Long 型 ID 序列化为字符串与时间格式强约束**。
   - 继承 `BaseEntity`/`TenantBaseEntity` 的实体，`id/createUser/updateUser` 已由基类 `@JsonSerialize(ToStringSerializer)` 处理，**无需重复加**。
   - 时间字段统一使用 `LocalDateTime`，全局 Jackson 配置序列化为 `yyyy-MM-dd HH:mm:ss`（时区 `GMT+8`），禁止散落使用裸 `java.util.Date`。

7. **后端代码注释不提前端**（memory `ypbin-code-comment-no-frontend`）。类/字段/方法注释只描述自身职责（"角色管理接口""登录响应"）。

8. **admin 只写业务代码；核心/基础/通用能力沉到 starter**（memory `admin-surface-dont-workaround`）。ypbin-admin 是脚手架落地的业务应用，只放业务逻辑；框架级、基础设施级、跨项目可复用的能力一律属于 `ypbin-starter`。

9. **Controller 写操作防御与极薄原则（必须齐备）**：
   - **权限校验**：所有写操作必须挂 `@SaCheckPermission("system:xxx:add|edit|delete")`。
   - **防重提交**：新增、修改、重置密钥、批量导入等写操作必须挂 `@Idempotent`（默认根据方法+参数指纹防短时间重复并发请求）。
   - **操作审计**：关键业务写操作必须挂 `@Log(value = "操作描述", module = "模块名称")`。
   - **Controller 极薄**：Controller 严禁包含复杂业务计算或数据库调用；仅做路由分发与 Service 编排；单类行数原则上不超过 400 行。

10. **事务、查询与枚举安全铁律**：
    - **显式回滚**：写操作 Service 方法的 `@Transactional` **一律显式声明 `rollbackFor = Exception.class`**。
    - **N+1 零容忍与 IN 查询短路**：严禁在 `for` / `stream.forEach` 循环体中执行数据库查询或外部 RPC；批量 `IN` 查询前**必须显式判空短路**（`if (CollectionUtils.isEmpty(ids)) return Collections.emptyList();`），严禁将空集合传入 `in(ids)` 导致 SQL 语法异常。
    - **集合禁返回 null**：返回 List/Set/Map 的方法查无数据时一律返回空集合（`Collections.emptyList()`/`List.of()`），严禁返回 null。
    - **枚举严禁 ordinal**：枚举必须显式定义 `private final int code` 与 `private final String desc`，数据库与接口传参一律使用 `code`，严禁使用 `Enum.ordinal()`。
    - **实体 equals 边界**：实体类的 `equals/hashCode` 严禁包含集合、关联对象或 `@TableField(exist=false)` 字段；必须且仅以主键 `id` 判等（如 `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`）。
    - **远程调用防雪崩**：所有 RPC/HTTP 调用必须显式指定 `connectTimeout` 和 `readTimeout`，明确降级与重试策略，禁止使用无超时的默认 Client。
    - **敏感词与 Excel 标准**：敏感词走 `@SensitiveWordFilter` AOP 切面；导入导出走 FastExcel `ExcelUtils`。

## 建议（YELLOW — 应遵循，有正当理由可偏离并说明）

- Controller 继承 `BaseController`（starter 的 `ypbin-starter-extension-crud`）拿 `ok()/fail()/userId()/ip()` 等（**仅单体版**；微服务版不继承，用 `R` 静态工厂 + `WebRequestUtils` + `IdentityContext`）；依赖用 `@RequiredArgsConstructor` + `final` 字段注入，不用 `@Autowired` 字段注入。
- Service 实现继承 `BaseServiceImpl<Mapper, Entity>`（starter 的 `ypbin-starter-extension-crud`）复用 `page/list/save/updateById/removeById/exists`，别自己重写分页。
- 写操作方法加 `@Transactional(rollbackFor = Exception.class)`（涉及多表尤其必须，参照 `createRole`/`updateRole`）。
- 唯一性校验抽成私有 `checkXxxUnique(value, excludeId)`，新增传 `null`、编辑传当前 id，重复抛 `BusinessException`（参照 [SysRoleServiceImpl.java:100](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/service/impl/SysRoleServiceImpl.java)）。
- 内置数据（如超管角色 id=1）做删除保护，抛 `BusinessException` 而非静默跳过。
- 查询条件用 `LambdaQueryWrapper` + 条件方法的 `condition` 参数（`.like(StringUtils.hasText(x), ...)`），不手拼 SQL；自定义 SQL 走 Mapper 的 `@Select` 且用 `#{}` 占位（防注入），参照 [SysRoleMapper.java:32](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/mapper/SysRoleMapper.java)。
- 需要展示创建人名字等关联文本，用 starter 的 `@RefText("user")` 注解（[RoleResp.java:43](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/model/resp/RoleResp.java)），不在 service 里手查拼名。
- 关键写操作加 `@Log(value = "...", module = "...")` 记操作日志（参照 `SysRoleController` 的 create/update/delete）。
- 命名/注释密度向同目录既有代码看齐。多租户业务实体继承 `TenantBaseEntity`，无租户隔离的继承 `BaseEntity`。

## 开发模式：新建一个 CRUD 模块配方

以 `role` 为最完整参照，一个模块通常包含这几层（包路径 `cn.ypbin.admin.modules.system`）：

```
entity/SysXxx.java            # 实体，继承 BaseEntity 或 TenantBaseEntity
mapper/SysXxxMapper.java      # extends BaseMapper<SysXxx>，自定义 SQL 用 @Select
model/query/XxxQuery.java     # 分页查询条件，extends PageQuery
model/req/XxxSaveReq.java     # 新增/编辑入参，带 jakarta.validation 校验
model/resp/XxxResp.java       # 出参
service/SysXxxService.java    # 接口，extends BaseService<SysXxx>
service/impl/SysXxxServiceImpl.java   # 实现，extends BaseServiceImpl<Mapper, Entity>
controller/SysXxxController.java      # 单体 extends BaseController；微服务不继承（R 静态工厂）
```

**Entity**（[SysRole.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/entity/SysRole.java)）：
```java
@Getter
@Setter
@TableName("sys_xxx")
public class SysXxx extends TenantBaseEntity {   // 无租户则 extends BaseEntity

    @Serial
    private static final long serialVersionUID = 1L;

    /** 名称 */
    private String name;
    // id/status/createUser/createTime/isDeleted 等由基类携带，不重复声明
}
```

**Controller**（[SysRoleController.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/controller/SysRoleController.java)）：
```java
@RestController
@RequestMapping("/system/xxx")
@RequiredArgsConstructor
public class SysXxxController extends BaseController {  // 微服务版：不继承，直接 @RestController + R 静态工厂

    private final SysXxxService xxxService;

    @GetMapping("/list")
    @SaCheckPermission("system:xxx:list")
    public R<PageResult<XxxResp>> list(XxxQuery query) {
        return ok(xxxService.pageXxx(query));
    }

    @Log(value = "新增XXX", module = "XXX管理")
    @PostMapping
    @SaCheckPermission("system:xxx:add")
    public R<Void> create(@Valid @RequestBody XxxSaveReq req) {
        xxxService.createXxx(req);
        return ok();
    }

    @Log(value = "修改XXX", module = "XXX管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:xxx:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody XxxSaveReq req) {
        xxxService.updateXxx(id, req);
        return ok();
    }

    @Log(value = "删除XXX", module = "XXX管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:xxx:delete")
    public R<Void> delete(@PathVariable Long id) {
        xxxService.deleteXxx(id);
        return ok();
    }
}
```
要点：路径 `/system/<模块>`；每个方法 `@SaCheckPermission("system:模块:动作")`；写操作 `@Log`；返回一律 `R<T>`（铁律5）；用 `@Valid @RequestBody` 触发校验。

**Service 实现**（[SysRoleServiceImpl.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/service/impl/SysRoleServiceImpl.java)）：
```java
@Service
@RequiredArgsConstructor
public class SysXxxServiceImpl extends BaseServiceImpl<SysXxxMapper, SysXxx> implements SysXxxService {

    @Override
    public PageResult<XxxResp> pageXxx(XxxQuery query) {
        PageResult<SysXxx> source = page(query, new LambdaQueryWrapper<SysXxx>()
            .like(StringUtils.hasText(query.getName()), SysXxx::getName, query.getName())
            .eq(query.getStatus() != null, SysXxx::getStatus, query.getStatus())
            .orderByAsc(SysXxx::getSort));
        List<XxxResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createXxx(XxxSaveReq req) {
        checkNameUnique(req.getName(), null);
        SysXxx entity = new SysXxx();
        BeanUtils.copyProperties(req, entity);   // 字段同名，直接拷贝，不改名（铁律1）
        save(entity);
    }

    private void checkNameUnique(String name, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysXxx>()
            .eq(SysXxx::getName, name)
            .ne(excludeId != null, SysXxx::getId, excludeId));
        if (exists) {
            throw new BusinessException("名称已存在：" + name);   // 暴露错误（铁律3）
        }
    }

    private XxxResp toResp(SysXxx entity) {
        XxxResp resp = new XxxResp();
        BeanUtils.copyProperties(entity, resp);
        return resp;
    }
}
```
要点：`BeanUtils.copyProperties` 靠字段同名直接拷（铁律1，别写 setter 改名）；写操作 `@Transactional`；错误抛 `BusinessException`（铁律3）；分页复用基类 `page(...)`。

**DTO**（**一律 `@Getter @Setter`，禁止 `@Data`**——防污染 equals/hashCode，且避免 `@Data` 的 `toString` 泄露 password 等敏感字段）：
- `XxxSaveReq`（[RoleSaveReq.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/model/req/RoleSaveReq.java)）：`@Getter @Setter`，字段带 `@NotBlank`/`@NotNull` 等 `jakarta.validation` 校验，message 写中文。
- `XxxResp`（[RoleResp.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/model/resp/RoleResp.java)）：`@Getter @Setter`，裸 `Long id` 加 `@JsonSerialize(ToStringSerializer)`（铁律6），关联名用 `@RefText`。
- `XxxQuery`（[RoleQuery.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/model/query/RoleQuery.java)）：`extends PageQuery`，`@Getter @Setter @EqualsAndHashCode(callSuper = true)`，只放过滤字段。

**Mapper**（[SysRoleMapper.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/modules/system/mapper/SysRoleMapper.java)）：`extends BaseMapper<SysXxx>`，基础 CRUD 自带；自定义查询用 `@Select` + `#{}` 占位。

所有类都补铁律4的 `@author wenbin` + `@since <当天日期>` 和 license 头。

## 审计模式：合规检查清单

审查后端 Java 时，逐条核对并输出「文件:行 → 违反第几条铁律 → 整改建议」。空表示通过。

| # | 检查点 | 快速定位 |
|---|--------|----------|
| R1 | 字段改名映射：service/DTO 层把一个字段名换成另一个 | 看 `toResp`/`copyProperties` 后有没有 `setB(a.getA())` 式改名；DB 列名与实体/JSON 是否同名 |
| R2 | 内联全限定类名 | 搜正文里的 `cn.ypbin.` / `java.` / `com.` 包路径前缀（import 行除外） |
| R3 | 静默降级：空 catch、失败静默 return、吞异常回退默认值 | 搜 `catch` 空块；批量容错处确认有 error 日志 |
| R4 | 类缺 `@author wenbin`/`@since`，或误用 `@date`/写了版本号 | 看每个类的类级 Javadoc |
| R5 | 用了 `ResponseEntity`/自定义 4xx/5xx，没走 `R<T>` | 搜 `ResponseEntity` / `HttpStatus` |
| R6 | 裸 `Long id` 出参未序列化为字符串（不经全局 json 模块时） | 看 Resp 里非基类的 Long 字段有无 `@JsonSerialize` |
| R7 | 注释点名前端框架/组件（vben/vxe-grid/RouteRecordRaw），或用前端字段名解释后端字段（"前端 userId""前端字段 permissions"）；泛指"前端"作消费方是灰区（宜改"客户端"，不判违规） | 搜注释里的 `vben` / `RouteRecordRaw` / `vxe` / `前端`，命中后区分硬违规与灰区 |
| R8 | 通用能力错落在 admin：admin 里自造了本该沉到 starter 的框架/基础/工具逻辑（换个业务项目也用得上），或写了绕开 starter 缺陷的临时实现 | 看 `common`/`util`/`config`/`handler`/`aspect` 类是否与具体业务无关；有无重复实现 starter 已有能力 → 建议沉 starter 或反馈补 starter |

审计命令示例（用 Grep 工具，内置 ripgrep，跨平台）：

```bash
# R2：正文内联 FQN（排除 import/package 行后人工确认）
rg -n "\b(cn\.ypbin|com\.baomidou|org\.springframework)\.[a-z]" --glob '*.java'
# R5：违规状态码用法
rg -n "ResponseEntity|HttpStatus\." --glob '*.java'
# R7：注释里的前端字样
rg -n "vben|RouteRecordRaw|前端契约" --glob '*.java'
```

> **本机 Git-bash 踩坑**：`grep -P`（PCRE，含中文字符类）在 Windows Git-bash 报 `-P supports only unibyte and UTF-8 locales`，跑不了。查中文/复杂模式一律用 **Grep 工具**（内置 ripgrep），不要用带 `-P` 的 shell grep。

## 常见踩坑（PITFALLS — 来自本项目真实修复，动手前先自检）

1. **异步落库的实体别继承 `BaseEntity`/`TenantBaseEntity`**。走 `@Async` 异步线程持久化的实体（如操作日志 `SysLog`），异步线程**没有 Sa-Token 上下文**，若继承基类走审计字段（createUser/updateUser）自动填充，会抛 `SaTokenContextException`。这类实体自带 `operateUser`/`operateTime` 等字段、不继承基类即可（参照 commit `de24d05` 把 `SysLog` 从 `BaseEntity` 摘出）。这是铁律8「无上下文线程取用户会抛异常」在 admin 侧的具体后果。
   - 建表脚本也要同步删掉这类表冗余的审计/逻辑删除列，与实体严格一致（铁律1：字段全程同名、库=实体一致）。

2. **多入口登录要统一收尾，避免各自构建 `LoginUser` 漏填字段**。账号/手机/第三方等多种登录入口若各自 `new LoginUser(...)`，极易漏填 `clientId/clientType/authType`，导致操作日志取不到客户端信息。抽一个统一的登录收尾（如 `LoginSupport`）集中回填客户端信息 + 记录在线终端，三种入口共用（参照 commit `de24d05` 的 `LoginSupport`）。

3. **配置类统一用 `@EnableConfigurationProperties` 注册，不标 `@Component`**（2026-08 全量审核定稿）。`XxxProperties` 类只放 `@ConfigurationProperties(prefix = ...)`（+ `@Data`/Lombok 生成 getter/setter，不手写），由启动类集中注册：`@EnableConfigurationProperties({AProperties.class, BProperties.class})`。散落 `@Component` 注册会让装配入口分散、且属性绑定时机不可控。参照 `AdminApplication` 对 `SecurityBootstrapProperties`/`LicenseIssuerProperties` 的统一注册。

4. **改配置后做「死配置键」检查**。`application.yml` 里已不存在的 `@ConfigurationProperties` 键（如历史上 `chat.window-size` 在配置类删除后残留）不会报错、永久静默失效，是全项目审核的高频发现项。改完配置类后 grep yml 里对应前缀键，逐一确认键仍被绑定；新增/删除配置项同步清理 yml。`spring-boot-configuration-processor` 的 metadata 无法覆盖手工 yml，只能人工核对。

5. **跨仓库版本/权限三处同步（改版本或权限前先自检）**：
   - **版本同步**：`ypbin-starter` 的 BOM 版本、`ypbin-admin` pom 的 `ypbin-starter.version`、`ypbin-site` 文档（`compatibility.md` 稳定版号 + `products/starter.md` Java 基线 + `guide/starter/modules/index.md` BOM 号）三处必须一致；发版后同步更新 site 的 releases/CHANGELOG。
   - **菜单/权限三向核对**：新增/修改一个菜单功能时，DB 菜单表（sys_menu 的 permission 列）、后端 `@SaCheckPermission("system:xxx:action")`、前端 `v-access:code` / `VbenTableAction` 的 `auth` 字符串三处权限码必须完全一致，缺一处该功能对相应角色不可见或越权。权限码格式 `system:模块:动作`（list/add/edit/delete/export 等）。

5b. **starter 依赖版本必须 = GitHub 最新 Release（CI 硬性校验）**（2026-09 实战：v2.1.0 Release 缺失曾致 main CI 全红）。
   - admin 的 CI 有一道「校验 starter 依赖版本为最新 Release」：`pom.xml` 的 `ypbin-starter.version` 必须等于 `https://api.github.com/repos/wenbin-wb/ypbin-starter/releases/latest` 的 tag（去 v 前缀），否则 **CI 直接失败**。升 starter 依赖前先查 latest release；starter 发版后（GitHub Release 建成）admin 需同步升依赖。
   - **`deploy/install.sh` 的 `STARTER_VERSION` 与 pom 的 `ypbin-starter.version` 必须一致**（两处漏改会导致部署脚本 .m2 检查与实际版本错位）。
   - **boot（单体）分支同样受版本校验约束**：boot pom 也必须跟随 latest release，不能长期停在旧版（曾因 v2.1.0 未正式发布而临时固定 2.0.0，发布后必须升回 2.1.x）。
   - **starter 发版必须是 GitHub Release 而非仅 git tag**：仅打 tag 不建 Release，`/releases/latest` 不会指向它（v2.1.0 因此缺失，CI 校验误判失败）。starter 的 release.yml 流水线会自动建 Release，若失败需人工补建。
   - **Nacos 共享配置（`deploy/nacos/*.yaml`）禁止硬编码数据库/密钥密码**：仓库内写 `${MYSQL_ROOT_PASSWORD}` 占位符，install.sh 导入 Nacos 前用 sed 替换为 `.env` 实际值。硬编码旧密码会导致新部署（.env 随机新密码）下服务连 MySQL `Access denied`、登录 500/409。

6. **实体继承体系：租户业务实体一律 `extends TenantBaseEntity`（Long tenantId），关系表用 `@Getter @Setter` 禁 `@Data`**（2026-08 全量审核定稿）。AI 模块曾用 `Integer tenantId` + 手写字段导致与 `sys_*` 的 `Long` 分叉、反复 Integer/Long 转换（已统一：7 实体 + 5 处 `currentTenantId()` + V7 迁移 `ALTER TABLE ... MODIFY tenant_id BIGINT`）。新实体：业务表继承 `TenantBaseEntity`（自动带审计/逻辑删除/租户列）；纯关联表（如 `SysRoleMenu`）`implements Serializable` + `@Getter @Setter`，**不用 `@Data`**（避免多余 equals/hashCode/toString）。
   - **建表必须补满 `BaseEntity` 全套公共列再继承**（2026-08 AI 实体规范化定稿）。`BaseEntity` 定义 `id + create_user/create_time/update_user/update_time/status/is_deleted`，`TenantBaseEntity` 再加 `tenant_id`。新建业务表 SQL 必须含这些列，实体才能继承基类；否则 MyBatis 生成 SQL 引用不存在列直接报错（如 `ai_chat_message` 缺 create_user）。**禁止为图轻量省略公共列**。确属纯追加大流量日志时才作受控例外：`implements Serializable` 自含字段 + 类级 Javadoc 明确批注原因，建表不含冗余审计列。

7. **写操作三件套：`@Transactional(rollbackFor = Exception.class)` + 业务 delete 方法 + `@Log` 覆盖**（2026-08 审核修复：App/Client 曾直透 `removeById`、create/resetSecret/update 缺事务、kickout/bind/unbind/testSend 缺 `@Log`）。
   - 每个写方法（create/update/delete/resetSecret/状态切换等）必须 `@Transactional(rollbackFor = Exception.class)`。
   - **禁止 Controller 直调基类 `removeById/removeByIds`**：Service 接口必须声明业务 delete 方法（`deleteApp`/`deleteClient` 等），内含存在性校验（不存在抛 `BusinessException`）再删除，Controller 只调业务方法。
   - 写接口补 `@Log(value = "...", module = "...")`；敏感读接口（下载授权文件、查看交付信息）允许保留 `@Log` 审计留痕。

8. **`@SaCheckPermission` 一律方法级**（类级只在确实整类同权时用，全库已收敛为方法级，参照 `SysLogController` 修复）。

9. **Controller 查询出参一律封装 `XxxResp`，禁止裸返实体**（2026-08 审核发现 SysMessage/SysJob/SysNotice/SysFile/AiKnowledgeBase/AiPromptTemplate 等裸返 `R<List<SysXxx>>`；已进规范，新写查询接口必须建 Resp 并在 Service 组装）。`@JsonSerialize(ToStringSerializer)` 字段级注解**无需再加**——starter-json 全局已把 Long/BigInteger/BigDecimal 转字符串，旧注解冗余但保留无害（渐进清理，不阻塞新代码）。

10. **配置/接口与 starter 的键级核对**：admin 的 `application.yml` 与 starter `@ConfigurationProperties` 键双向核对（防死键）；`@Value`/占位符直读（如 `JobInitializer` 的 `fixedDelayString`）属可接受的轻量用法，但新写配置优先走强类型 Properties。

## 验证

本机命令行无 java/mvn，走 IntelliJ 内置工具链编译（memory `build-env`）。admin 开发**只编译验证不启动服务**，统一后面测试（memory `ypbin-admin-workflow`）。改完让用户在 IDEA 里 Build 或用其内置 Maven 编译对应模块，确认无编译错误再交付。

分层落位（铁律8）：动手前先想这段代码属于 admin（业务）还是 starter（通用）。属通用就沉/补 starter 再引用；starter 现有能力不合用先反馈用户去优化 ypbin-starter，别在 admin 里默默写绕开逻辑（memory `admin-surface-dont-workaround`）。
