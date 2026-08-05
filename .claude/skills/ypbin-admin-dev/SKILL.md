---
name: ypbin-admin-dev
description: ypbin-admin 后端开发与合规审计标准。开发任何 Controller/Service/Entity/DTO/Mapper，或审查已有后端 Java 代码是否合规时使用。强制字段全程同名、禁止字段改名映射、禁止内联全限定类名、禁止静默降级、异常统一 200、类必带 @author wenbin + @since、注释不提前端、admin 只写业务通用能力沉 starter。
---

# ypbin-admin-dev — ypbin-admin 后端标准

面向 `ypbin-admin`（Spring Boot 3 + MyBatis-Plus + Sa-Token 多模块，构建在自研 `ypbin-starter` 之上）。模块划分：`ypbin-admin-common`（公共）、`ypbin-admin-system`（业务）、`ypbin-admin-server`（启动 + 装配）。两种模式：

- **开发模式**：写新 Controller/Service/Entity/DTO/Mapper 时，严格按下面的配方与铁律落地。
- **审计模式**：审查已有代码时，逐条对照「铁律」与「审计清单」，输出违规项 + 整改建议。

作者署名统一 `wenbin`。改动只编译验证不启动服务（见项目 memory `ypbin-admin-workflow`）。

> **skill 内的 starter 引用说明**：本文提到的 `BaseController`/`BaseServiceImpl`/`GlobalExceptionHandler`/`BaseEntity`/`TenantBaseEntity`/`R`/`PageResult`/`PageQuery`/`BusinessException`/`@RefText`/`@Log` 等都在**另一个仓库 `ypbin-starter`** 里，不在 admin 内。文中用类名 + 所属 starter 模块标注（如 `ypbin-starter-extension-crud`），需要看源码时去 `ypbin-starter` 对应模块找，不做跨仓库文件链接（单独打开 admin 时点不到）。

## 铁律（RED — 违反即判不合格，必须整改）

这些是项目硬约束，**全部不可豁免**。它们来自用户明确表态（见各条关联的 memory），不是可商量的风格偏好。

1. **字段全程同名，禁止任何字段改名映射**（memory `ypbin-no-field-mapping`）。DB 列（下划线）= 实体字段（驼峰）= DTO 字段 = 接口 JSON，全链路同一个名字。
   - MyBatis `map-underscore-to-camel-case` 的下划线↔驼峰**不算映射**（标准命名转换，允许）。
   - 禁止的是「改名」：`nickname→realName`、`parentId→pid` 这种换名字。要对齐某个字段名，就直接把 DB 列建成那个名字（要 `pid` 就建 `pid` 列），不要留旧列名再在 service/DTO 层转。
   - **拼装 ≠ 映射**：多表查出来组装成嵌套对象（如 `RoleResp` 里 `BeanUtils.copyProperties` 后再 `setPermissions(mapper.selectMenuIdsByRoleId(id))`），字段名全程不变，这是结构组装，允许。参照 [SysRoleServiceImpl.java:118](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/service/impl/SysRoleServiceImpl.java) 的 `toRespWithMenus`。

2. **禁止内联全限定类名，一律 import**（memory `ypbin-no-inline-fqn`）。方法签名/变量/返回类型/`new`/静态调用里凡引用其它包的类，先在文件顶部 `import`，正文只写简单类名（`RoleResp`，不是 `cn.ypbin.admin.system.model.resp.RoleResp`）。已有内联 FQN 视为违规。评审自己产出的 Java 专门查这一条。

3. **不做掩盖问题的静默降级，错误要暴露**（memory `ypbin-no-silent-fallback`）。
   - 禁止空 `catch {}` 吞异常、失败静默 `return`、失败回退默认值假装正常、"配置缺失则降级"。
   - 可预期的业务错误抛 `BusinessException`（会被全局处理器转成 200 + 业务码，参照 [SysRoleServiceImpl.java:78](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/service/impl/SysRoleServiceImpl.java) `throw new BusinessException("角色不存在")`）。配置缺失该显式报错/warn 日志，不静默。
   - **例外（合理容错，非降级）**：批量操作中单元素失败不阻断整体（如群发通知某用户邮件失败），但**必须记 error 日志**。判据：错误是否被"看见"——看不见的就是坏降级。

4. **每个类的类级 Javadoc 末尾必带 `@author wenbin` + `@since <当天日期>`**（memory `ypbin-code-conventions`）。
   - 用 `@since`，**不用 `@date`**（非标准标签，IDEA 报未知标签黄线）；**不写版本号**（难维护，用户明确不要）。
   - 顶部 Apache-2.0 license 块注释保留（admin 是 `2026-present ypbin-admin authors`，starter 是 `2024-present ypbin-starter authors`，别搞混）。
   - 格式见 [SysRole.java:18](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/entity/SysRole.java)：
     ```java
     /**
      * 系统角色。
      *
      * @author wenbin
      * @since 2026-08-05
      */
     ```

5. **异常统一 HTTP 200，靠 `R.code` 区分成功/失败**（memory `ypbin-code-conventions`）。Controller 一律返回 `R<T>`，走 `BaseController` 的 `ok()/ok(data)/data(...)`；错误抛异常交给全局 `GlobalExceptionHandler`（starter 的 `ypbin-starter-web` 模块）。**禁止 `ResponseEntity.status(4xx/5xx)`** 或自定义 REST 语义状态码。

6. **Long 型 ID 序列化为字符串**（memory `ypbin-code-conventions`）。继承 `BaseEntity`/`TenantBaseEntity` 的实体，`id/createUser/updateUser` 已由基类 `@JsonSerialize(ToStringSerializer)` 处理，**无需重复加**。DTO 里裸 `Long id` 若不经全局 json 模块，按 `RoleResp` 那样显式 `@JsonSerialize(using = ToStringSerializer.class)`（[RoleResp.java:28](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/model/resp/RoleResp.java)）。别用 `Number()` 思维在后端截断精度。

7. **后端代码注释不提前端**（memory `ypbin-code-comment-no-frontend`）。类/字段/方法注释只描述自身职责（"角色管理接口""登录响应"）。分两档判定：
   - **硬违规（必改）**：① 点名前端框架/组件/类型——`vben`/`vxe-grid`/`RouteRecordRaw` 等；② 用前端字段名给后端字段贴标签——"前端 userId""前端字段 permissions""字段名对齐前端 XxxApi"。字段名对齐关系记在 DEVELOPMENT-PLAN.md，不进代码注释。
   - **允许（灰区，宜用词更稳）**：把消费方作泛指来描述本方法/字段职责（"构建路由树供客户端渲染"）。这类不算违规，但**优先写"客户端"而非"前端"**，彻底与具体前端解耦。

8. **admin 只写业务代码；核心/基础/通用能力沉到 starter**（memory `admin-surface-dont-workaround`）。ypbin-admin 是脚手架落地的业务应用，只放业务逻辑（具体的角色/菜单/用户等模块）；框架级、基础设施级、跨项目可复用的能力一律属于 `ypbin-starter`。
   - **判据**：这段代码换一个业务项目还用得上吗？用得上 → 它属于 starter，不属于 admin。（如：通用分页、统一响应、异常处理、租户拦截、日志切面、JSON 序列化、加解密、文件存储抽象、通用工具类。）
   - **starter 已有就复用，别在 admin 重造**：先按 `ypbin-starter-core / -web / -data / -extension-crud / -extension-tenant / -json / -log / -security` 等模块找现成能力，找到就用（如继承 `BaseServiceImpl`、返回 `R<T>`、`@RefText`/`@Log`）。
   - **starter 还没有该通用能力就去补 starter**：新增/扩展对应 starter 模块，再在 admin 引用；**不要**在 admin 里先临时实现一份通用逻辑「以后再抽」。
   - **starter 现有能力不合用时先反馈用户**：报告缺口/别扭点，由用户决定去优化 starter，**别在 admin 里默默写绕开逻辑**掩盖 starter 的不足。
   - 例外：确属本业务独有、其它项目复用不了的逻辑，就留在 admin。判不准时先问，不擅自把业务代码塞进 starter，也不把通用能力沉淀漏在 admin。

## 建议（YELLOW — 应遵循，有正当理由可偏离并说明）

- Controller 继承 `BaseController`（starter 的 `ypbin-starter-extension-crud`）拿 `ok()/fail()/userId()/ip()` 等；依赖用 `@RequiredArgsConstructor` + `final` 字段注入，不用 `@Autowired` 字段注入。
- Service 实现继承 `BaseServiceImpl<Mapper, Entity>`（starter 的 `ypbin-starter-extension-crud`）复用 `page/list/save/updateById/removeById/exists`，别自己重写分页。
- 写操作方法加 `@Transactional(rollbackFor = Exception.class)`（涉及多表尤其必须，参照 `createRole`/`updateRole`）。
- 唯一性校验抽成私有 `checkXxxUnique(value, excludeId)`，新增传 `null`、编辑传当前 id，重复抛 `BusinessException`（参照 [SysRoleServiceImpl.java:100](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/service/impl/SysRoleServiceImpl.java)）。
- 内置数据（如超管角色 id=1）做删除保护，抛 `BusinessException` 而非静默跳过。
- 查询条件用 `LambdaQueryWrapper` + 条件方法的 `condition` 参数（`.like(StringUtils.hasText(x), ...)`），不手拼 SQL；自定义 SQL 走 Mapper 的 `@Select` 且用 `#{}` 占位（防注入），参照 [SysRoleMapper.java:32](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/mapper/SysRoleMapper.java)。
- 需要展示创建人名字等关联文本，用 starter 的 `@RefText("user")` 注解（[RoleResp.java:43](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/model/resp/RoleResp.java)），不在 service 里手查拼名。
- 关键写操作加 `@Log(value = "...", module = "...")` 记操作日志（参照 `SysRoleController` 的 create/update/delete）。
- 命名/注释密度向同目录既有代码看齐。多租户业务实体继承 `TenantBaseEntity`，无租户隔离的继承 `BaseEntity`。

## 开发模式：新建一个 CRUD 模块配方

以 `role` 为最完整参照，一个模块通常包含这几层（包路径 `cn.ypbin.admin.system`）：

```
entity/SysXxx.java            # 实体，继承 BaseEntity 或 TenantBaseEntity
mapper/SysXxxMapper.java      # extends BaseMapper<SysXxx>，自定义 SQL 用 @Select
model/query/XxxQuery.java     # 分页查询条件，extends PageQuery
model/req/XxxSaveReq.java     # 新增/编辑入参，带 jakarta.validation 校验
model/resp/XxxResp.java       # 出参
service/SysXxxService.java    # 接口，extends BaseService<SysXxx>
service/impl/SysXxxServiceImpl.java   # 实现，extends BaseServiceImpl<Mapper, Entity>
controller/SysXxxController.java      # extends BaseController
```

**Entity**（[SysRole.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/entity/SysRole.java)）：
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

**Controller**（[SysRoleController.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/controller/SysRoleController.java)）：
```java
@RestController
@RequestMapping("/system/xxx")
@RequiredArgsConstructor
public class SysXxxController extends BaseController {

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

**Service 实现**（[SysRoleServiceImpl.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/service/impl/SysRoleServiceImpl.java)）：
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

**DTO**：
- `XxxSaveReq`（[RoleSaveReq.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/model/req/RoleSaveReq.java)）：`@Data`，字段带 `@NotBlank`/`@NotNull` 等 `jakarta.validation` 校验，message 写中文。
- `XxxResp`（[RoleResp.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/model/resp/RoleResp.java)）：`@Data`，裸 `Long id` 加 `@JsonSerialize(ToStringSerializer)`（铁律6），关联名用 `@RefText`。
- `XxxQuery`（[RoleQuery.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/model/query/RoleQuery.java)）：`extends PageQuery`，`@Getter @Setter @EqualsAndHashCode(callSuper = true)`，只放过滤字段。

**Mapper**（[SysRoleMapper.java](ypbin-admin-system/src/main/java/cn/ypbin/admin/system/mapper/SysRoleMapper.java)）：`extends BaseMapper<SysXxx>`，基础 CRUD 自带；自定义查询用 `@Select` + `#{}` 占位。

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

## 验证

本机命令行无 java/mvn，走 IntelliJ 内置工具链编译（memory `build-env`）。admin 开发**只编译验证不启动服务**，统一后面测试（memory `ypbin-admin-workflow`）。改完让用户在 IDEA 里 Build 或用其内置 Maven 编译对应模块，确认无编译错误再交付。

分层落位（铁律8）：动手前先想这段代码属于 admin（业务）还是 starter（通用）。属通用就沉/补 starter 再引用；starter 现有能力不合用先反馈用户去优化 ypbin-starter，别在 admin 里默默写绕开逻辑（memory `admin-surface-dont-workaround`）。
