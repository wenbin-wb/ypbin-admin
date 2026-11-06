# starter 4.4.0 升级回归清单（本仓结论）

> 对应 issue #129。`ypbin-starter` 4.3.0 → 4.4.0 含若干**运行时行为变更**，单测覆盖不到。
> 本文记录每一项在本仓的代码级核对结论，以及必须在部署后执行的验收动作（部署验收时逐条勾掉）。

## 1) `TreeUtils.buildAndFilter` 祖先链（starter #162）

- **本仓结论：无影响。** 全仓使用点是 `TreeUtils.build(...)` 的函数式重载
  （`SysDeptServiceImpl` / `SysMenuServiceImpl` / `SysSpaceServiceImpl`），没有调用 `buildAndFilter`，
  不存在「按关键字过滤树」的入口。
- 部署后验收：无需专项验证；若将来新增树过滤入口，再按 starter 的语义（命中节点保留祖先链）核对。

## 2) 平台访问守卫装配（starter #150 相关）

- **本仓结论：无需改动。** `@PlatformAccess` 全部是**类级**注解（Dashboard/Mail/AiModelConfig 等 controller），
  4.4.0 的切点改为「实现方法 → 目标类 → 接口方法 → 接口类」解析，类级 + CGLIB 的行为与升级前一致；
  `PlatformUserChecker` 由 `AiPlatformUserChecker` 提供，`PermissionProvider` 缺失路径为 fail-closed（拒绝 + WARN）。
- 部署后验收：平台管理员访问正常；临时移除 checker（或改名）确认被拒绝而不是放行。

## 3) 数据权限作用域跨线程传播

- **本仓结论：无行为回归风险。** 数据权限处理器 `AdminDataScopeHandler` 只治理 `sys_user`；
  异步路径（`AiDocumentVectorizer`、`AiKnowledgeBizService`、`DbLogProviders`）不查询带 `dept_id` 的实体。
- 部署后验收：若新增 `@Async`/线程池查询 `sys_user` 的逻辑，确认异步线程内数据范围仍生效（越权查询应被限制）。

## 4) 操作日志默认脱敏（starter #152）

- **决策：接受默认脱敏。** `LogMaskModule` 会按字段名/参数名/请求头名掩码口令、令牌、密钥类字段。
  本仓日志落 `sys_log`，排查时这类字段将显示为掩码。
- 部署后验收：确认没有依赖明文口令/令牌的运维脚本或告警规则；如有，改用业务侧显式日志（不要关掉全局脱敏）。

## 5) 415 与入口级 4xx 的前端提示

- **本仓结论：前端无需改动。** `apps/web-antd/src/api/request.ts` 只对业务 `code=401` 做重登处理，
  非 2xx（403/415/429）走 axios error 分支读取 `response.data.message`，能展示后端 `R` 信封里的中文提示
  （入口拒绝响应体在 4.3.0 起已是 `R`）。
- 部署后验收：用错误 `Content-Type` 调一次接口，确认前端提示可读（非「网络异常」）。

## 6) CORS 默认收紧（starter #159）

- **本仓结论：当前部署无影响。** `deploy/nginx-microservice.conf` 同域反代 `/api` → 网关，属同源；
  `deploy/nacos` 未配置任何 CORS。
- 部署后验收：若接入独立域名前端（或本地直连后端），必须显式配置 `ypbin.gateway.cors` / `ypbin.web.cors`，
  且禁止 `allowed-origin-patterns=*` + `allow-credentials=true`（组合会启动失败）。

## 7) 自动封禁 `allow-non-public-ip` 默认 false（starter #151）

- **决策：保持默认（内网地址不参与自动封禁）。** 本仓网关/服务均部署在容器私网内，
  真实客户端地址由 `ClientIpResolver` + `trusted-proxies` 解析；`exclude-ips` 仍是第一道防线，
  避免把网关注入封禁表。确需封禁内网攻击者时再显式开
  `ypbin.security.blocklist.auto-ban.allow-non-public-ip: true`，并先确认 `exclude-ips` 覆盖网关。

## 8) 客户端 IP 口径（配套 issue #125 / #128）

- 代码：登录/登出/短信/社交与 AI 公开分享共 7 处改用 `WebRequestUtils.clientIp(ClientIpResolver)`。
- 配置：`blocklist.trusted-proxies` 与 `tools.trusted-proxies` 在 auth/system/ai 统一为网关地址
  `172.20.0.20/32`（compose 固定 IP）；网关信任入口 nginx `172.20.0.40/32`；system 补私网 `exclude-ips`。
- 部署后验收：
  1. 带伪造 `X-Forwarded-For`（如 `<伪造私网>, <真实地址>`）登录，确认失败计数与 `sys_log.ip` 记的是真实地址；
  2. 手工往 Redis 写网关地址的 ban 键，确认 system/auth 仍返回 200（`exclude-ips` 生效）；
  3. 若改用宿主机 OpenResty 直连网关，需把 `172.20.0.1/32` 一并加入网关 `trusted-proxies`。
