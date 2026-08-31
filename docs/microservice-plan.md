# ypbin-admin 微服务化改造方案（企业级）

> 状态：**方案 v1.2**（2026-09-01，M2 认证链路已落地）
> 分支：`feature/microservice`（与 `main` 单体版并行维护）
> 理念：**复用 ypbin-starter 的 cloud 系列底座，不另起炉灶；CONTRACT.md 对外契约不变，前端零改动切换**。

---

## 0. 设计原则（不脱离项目理念）

1. **契约先行**：CONTRACT.md 已定义单体/微服务统一的响应、错误码、分页、序列化、鉴权头契约。微服务版严格遵循，**前端无需感知后端形态**。
2. **底座复用**：starter 的 `cloud-core/gateway/nacos/loadbalancer/observability/sentinel` 就是为微服务准备的，直接装配而非重写。
3. **增量演进**：不改业务代码逻辑，只改部署形态与模块边界；单体版继续在 `main` 维护。
4. **领域驱动**：按 admin 现有 13 个 API 域 + 37 张表的天然边界切分服务，不臆造新域。
5. **渐进式拆分**：先"单库多服务"（共享库降低迁移风险），稳定后按域拆库。

---

## 1. 目标架构总览

```
                        ┌─────────────────────────────┐
                        │     前端 admin-ui (不变)      │
                        └──────────────┬──────────────┘
                                       │ HTTP + Token (CONTRACT 契约)
                                       ▼
                        ┌─────────────────────────────┐
                        │   API Gateway (ypbin-gateway)│
                        │  - 统一鉴权(网关校验 token)   │
                        │  - 路由转发 / 限流 / 熔断      │
                        │  - 清洗外部头,签发内部身份头    │
                        │  - Swagger 聚合 / 跨域         │
                        └──────────────┬──────────────┘
                                       │ 内部身份头 (X-User-Id 等) + Feign
          ┌──────────────┬──────────────┼──────────────┬──────────────┐
          ▼              ▼              ▼              ▼              ▼
   ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐
   │ system-svc │ │  ai-svc    │ │  auth-svc  │ │  gateway   │ │  job-svc   │
   │ 用户/角色/  │ │ 知识库/对话 │ │ 登录/注册  │ │(独立部署)   │ │ 定时任务   │
   │ 菜单/部门/  │ │ 模型/统计  │ │ 社交登录   │ │            │ │ 公告推送   │
   │ 字典/配置/  │ │ 分享/Wiki  │ │ 验证码    │ │            │ │ 站内信     │
   │ 租户/日志/  │ │ 文件/存储  │ │           │ │            │ │           │
   │ 公告/邮件   │ │           │ │           │ │            │ │           │
   └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘
         └──────────────┴──────┬──────┴──────────────┴──────────────┘
                               ▼
              ┌─────────────────────────────┐
              │  基础设施 (docker-compose)   │
              │  Nacos(注册/配置) Redis      │
              │  MySQL(共享库,渐进拆库)       │
              │  Sentinel-Dashboard         │
              │  Prometheus+Grafana(可选)   │
              └─────────────────────────────┘
```

## 2. 服务拆分矩阵（基于现有 API 域）

| 新服务 | 原 API 域 | 核心表 | 说明 |
|---|---|---|---|
| **auth-svc** | `/auth/*`、`/captcha`、`/user/profile`、`/user/messages`、`/open-api` | sys_user、sys_client、sys_app、sys_auth_template、sys_user_social、sys_message | 认证/个人中心/站内信（高安全域，独立） |
| **system-svc** | `/system/user|role|menu|dept|post|dict|dict-item|config|tenant|log|online-user|notice|mail|license` | sys_user 等 20+ 表 | 核心管理域（最大，可再拆） |
| **ai-svc** | `/ai/*`、`/share`、`/widget` | ai_* 12 表 | AI 独立演进（模型/向量库隔离） |
| **job-svc** | `/system/job` + 调度执行 | sys_job、sys_job_log | 定时任务独立（执行器隔离） |
| **file-svc**（可选二期） | `/system/file` | sys_file | 文件存储独立（OSS/本地） |

> **一期**：auth + system + ai + job 四服务（覆盖全部 API），共享 MySQL 库。
> **二期**：按域拆库（auth 库、system 库、ai 库），引入分布式事务兜底（Seata 可选）。

## 3. 模块工程结构（admin 仓库内）

```
ypbin-admin/
├── pom.xml                          # ${revision} + flatten（沿用现机制）
├── ypbin-admin-common/              # 保留：常量/工具/跨服务 DTO
├── ypbin-admin-server/              # 保留：单体版启动器（main 分支）
├── ypbin-admin-system/              # 保留：单体版业务（main 分支）
│
├── ypbin-microservice/              # 新增：微服务聚合模块（仅此分支）
│   ├── ypbin-gateway/               # 网关服务（starter-cloud-gateway）
│   ├── ypbin-auth-service/          # 认证服务
│   ├── ypbin-system-service/        # 系统管理服务
│   ├── ypbin-ai-service/            # AI 服务
│   ├── ypbin-job-service/           # 任务服务
│   ├── ypbin-common-api/            # 跨服务 Feign 接口 + 共享 DTO
│   └── deploy/                      # 微服务 docker-compose + 各服务配置
└── docs/microservice-plan.md        # 本方案
```

**代码复用策略**：单体版 `ypbin-admin-system` 的 service/mapper/entity 按域**复制**到各服务（先复制后抽公共），避免破坏单体版；`ypbin-admin-common` 提升为共享依赖。

## 4. 关键技术设计

### 4.1 网关（ypbin-gateway）
- 基于 `ypbin-starter-cloud-gateway` + Nacos 动态路由
- **统一鉴权**：校验 `Authorization` token（sa-token 网关模式），校验通过后**清洗外部头**、签发内部身份头（`X-User-Id/X-User-Name/X-Tenant-Id/X-Roles`，见 CONTRACT.md §6）
- 限流/熔断：starter-cloud-sentinel 网关适配
- Swagger 聚合：starter-cloud-gateway 内置

### 4.2 服务间调用（Feign）
- `ypbin-common-api` 定义 Feign 接口（如 `AuthFeignClient`、`UserFeignClient`）
- `ypbin-starter-cloud-core` 的 Feign 拦截器**自动透传内部身份头**（CONTRACT 已约定）
- 超时/重试：starter 铁律要求显式配置 connectTimeout/readTimeout

### 4.3 认证链路
- 登录在 auth-svc；token 存 Redis（sa-token 支持）
- **网关校验 token → 下发身份头 → 下游服务信身份头**（下游不再查库验 token）
- 在线用户：auth-svc 维护，`/system/online-user` 经 Feign 调 auth-svc

### 4.4 数据
- 一期：共享 MySQL（`ypbin_admin` 库），各服务只操作自己的表（表权限隔离）
- 二期：按域拆库 + Seata AT 模式兜底分布式事务（如"用户+角色+部门"跨域写）

### 4.5 定时任务（job-svc）
- starter-job 的 `JobManager` 支持集群防重（依赖 LockService）
- admin 现有 `JobManager` 迁移到 job-svc，`/system/job` 管理接口走 Feign

### 4.6 可观测性
- starter-cloud-observability：`X-Request-Id` 全链路 MDC
- starter-cloud-sentinel：限流熔断指标
- 可选：Prometheus + Grafana 大盘

## 5. 迁移路线图（4 个里程碑）

| 里程碑 | 内容 | 验证 |
|---|---|---|
| **M1 骨架** | 建 `ypbin-microservice` 聚合 + 网关 + 四服务空壳 + Nacos/docker-compose | 网关转发到各服务，健康检查通过 |
| **M2 认证链路** ✅ | 网关 `SaTokenGatewayAuthProvider`（sa-token 无状态校验 + 签发身份头）；common-api `IdentityHeaderFilter`/`IdentityContext`（下游信身份头，不绑 sa-token） | 编译通过；端到端联调待基础设施就绪 |
| **M3 业务迁移** ✅(system) | system-svc 迁入用户/角色/菜单/部门/岗位/配置域（27 实体+27 mapper+6 controller），依赖收敛至所需 starter | 编译通过；ai/job 待迁入，端到端联调待基础设施 |
| **M4 加固上线** | Sentinel 规则、链路追踪、配置中心化、灰度发布、监控告警 | 压测 + 故障演练 + 灰度 |

## 6. 双版本并行维护策略

| 事项 | 单体版（main） | 微服务版（feature/microservice） |
|---|---|---|
| 分支 | main（稳定） | feature/microservice（演进） |
| 公共底座 | starter（同版本） | starter（同版本） |
| 业务代码 | system 模块内 | 拆分到各服务 |
| Bug 修复 | main 修 | cherry-pick 到 microservice |
| 新功能 | main 先做 | 稳定后合入 microservice |
| 部署 | 单 jar + deploy/ | docker-compose 多服务 |
| 契约 | CONTRACT.md | 同 CONTRACT.md（前端零改动） |

**代码同步机制**：`ypbin-admin-common` 与 `ypbin-admin-system` 的**公共部分**（常量/工具/枚举）定期从 main cherry-pick 到 microservice；业务逻辑在 microservice 分支内各自演进，不再反向合回（避免冲突）。

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 拆分破坏单体版 | 新分支隔离，单体版 main 不动；业务代码先复制后抽离 |
| 分布式事务 | 一期共享库规避；二期 Seata AT 兜底 |
| 跨服务调用链复杂 | CONTRACT 身份头约定 + Feign 透传自动化；链路 ID 全链路 |
| 网关单点 | 网关无状态，多实例部署 + Nacos 负载均衡 |
| 前端适配 | 契约不变，前端零改动；仅网关地址变化（环境变量） |

## 8. 与市面大型架构对标

| 能力 | 本方案 | 对标（Spring Cloud Alibaba 生态） |
|---|---|---|
| 注册/配置中心 | Nacos | 同 |
| 网关 | Spring Cloud Gateway + 统一鉴权 | 同（阿里云网关同思路） |
| 鉴权 | sa-token 网关模式 + 身份头 | 对标 JWT/OAuth2 网关校验 + 下游信头 |
| 服务调用 | OpenFeign + 身份头透传 | 同 |
| 限流熔断 | Sentinel | 同 |
| 分布式事务 | 二期 Seata | 同 |
| 链路追踪 | X-Request-Id + 可选 SkyWalking/Prometheus | 对标 SkyWalking 全链路 |
| 部署 | docker-compose（可演进 K8s） | 对标 K8s 云原生 |

---

## 附：一期服务清单（M1 交付物）

```
ypbin-microservice/
├── pom.xml                     # 聚合 pom（${revision}）
├── ypbin-gateway/              # 端口 18080
├── ypbin-auth-service/         # 端口 18081
├── ypbin-system-service/       # 端口 18082
├── ypbin-ai-service/           # 端口 18083
├── ypbin-job-service/          # 端口 18084
├── ypbin-common-api/           # Feign 接口 + 共享 DTO
└── deploy/
    ├── docker-compose.yml      # nacos/redis/mysql/sentinel + 四服务
    └── .env.example            # 凭据环境变量
```
