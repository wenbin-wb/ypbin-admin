# ypbin-admin 微服务版部署指南

> 微服务版（main 分支）：网关 + 认证 + 系统 + AI + 任务 五服务架构，
> 基于 Spring Cloud Alibaba（Nacos 注册/配置中心 + Sentinel 限流 + OpenFeign 服务调用）。

## 架构

| 服务 | 端口 | 说明 |
|---|---|---|
| `ypbin-gateway` | 18080 | Spring Cloud Gateway，统一鉴权（sa-token 无状态校验 + 签发内部身份头） |
| `ypbin-auth` | 18081 | 认证服务：账号/手机验证码/第三方登录、验证码、sa-token 登录态 |
| `ypbin-system` | 18082 | 系统服务：用户/角色/菜单/部门/岗位/字典/文件/日志/任务/通知/租户等 |
| `ypbin-ai` | 18083 | AI 服务：知识库/对话/模型/统计 |
| `ypbin-job` | 18084 | 定时任务调度 |
| `nacos` | 8848 | 注册中心 + 配置中心（共享配置 `ypbin-common.yaml`） |
| `sentinel-dashboard` | 8858 | 限流控制台（可选） |
| `mysql` | 3306 | 共享数据库（所有服务共用 `ypbin_admin` 库） |
| `redis` | 6379 | 缓存（验证码/会话/多级缓存） |

## 服务间调用约定

- **auth/ai 不直连共享库**：用户/权限/社交绑定等数据一律经 `ISystemClient` Feign 调 `ypbin-system`
- **身份头模式**：网关校验 token 后签发 `X-User-Id/X-User-Name/X-Tenant-Id/X-Dept-Id/X-Roles`，
  下游服务经 `IdentityContext` 读取当前用户（非 sa-token 会话）
- **Feign 目录规范**：
  - api 模块 `ypbin-system-api/.../api/feign/`：`ISystemClient`（接口）+ `ISystemClientFallback`（降级）
  - service 模块 `ypbin-system/.../feign/`：`SystemClientImpl`（`@RestController` 实现）

## 前置条件

- Ubuntu/Debian 服务器，root 权限
- Docker + Docker Compose
- 网络可访问 GitHub、Maven Central、Docker Hub

## 一键部署（推荐）

**方式一：一键脚本**（自动完成环境检查→拉取 starter/admin→构建 starter→打包 5 服务→启动→健康检查）：

```bash
# Docker 模式（生产服务器，含 Nacos/Redis/MySQL 基础设施）
bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh)

# 无 Docker 模式（本机/轻量服务器，java -jar 直接启动；需外部 Nacos/Redis/MySQL）
NO_DOCKER=1 NACOS_ADDR=localhost:8848 DB_HOST=localhost DB_USER=root DB_PASSWORD=xxx \
  bash deploy/install.sh
```

自定义参数（环境变量）：`YPBIN_ROOT`（部署根目录，默认 /opt/ypbin/main）、`BRANCH`（默认 main）、`NACOS_ADDR`、`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`、`REDIS_HOST/REDIS_PORT`、`MYSQL_ROOT_PASSWORD`（Docker 模式内建 MySQL 密码）。

**方式二：手动**：

```bash
# 拉取 microservice 分支
git clone -b main https://github.com/wenbin-wb/ypbin-admin.git
cd ypbin-admin/deploy

# 配置环境变量
cp .env.example .env   # 修改敏感项（MYSQL_ROOT_PASSWORD 等）

# 启动（基础设施 + 5 微服务）
docker compose -f docker-compose.yml up -d --build
```

> 各服务 Dockerfile 位于服务目录内（`ypbin-gateway/Dockerfile` 等），compose 构建自动使用。

### Nacos 配置中心（可选，推荐）

默认各服务本地 yml 已含全部配置（nacos 地址/限流/租户开关），无 Nacos 配置中心也可运行。
生产建议创建共享配置统一管理：

1. 登录 Nacos 控制台（`http://<host>:8848/nacos`，默认账号 `nacos/nacos`）
2. 新建配置：Data ID `ypbin-common.yaml`、Group `DEFAULT_GROUP`、格式 YAML
3. 内容见 `deploy/nacos/ypbin-common.yaml`（限流 dashboard 地址等，可覆盖各服务默认值）

各服务通过 `spring.config.import: optional:nacos:ypbin-common.yaml` 引入，
本地开发无该配置时静默跳过（optional 前缀），不影响启动。

## 手动构建部署

```bash
# 1. 构建（需要 JDK 21 + Maven）
export JAVA_HOME=/path/to/jdk21
mvn -DskipTests clean package

# 2. 打镜像（每个服务）
docker build -t ypbin/ypbin-gateway:local ypbin-gateway/
docker build -t ypbin/ypbin-auth:local ypbin-auth/
docker build -t ypbin/ypbin-system:local ypbin-service/ypbin-system/
docker build -t ypbin/ypbin-ai:local ypbin-service/ypbin-ai/
docker build -t ypbin/ypbin-job:local ypbin-service/ypbin-job/

# 3. 编排启动
docker compose -f deploy/docker-compose.yml up -d
```

## 验证

```bash
# 网关健康检查
curl http://localhost:18080/actuator/health

# 登录（经网关）
curl -X POST http://localhost:18080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123456"}'
# 返回 { code:200, data:{ tokenValue:"..." } }

# 带 token 访问业务接口（网关校验后签发身份头转发）
curl http://localhost:18080/system/user/list \
  -H "Authorization: Bearer <token>"

# Nacos 控制台确认 5 服务注册
# http://localhost:8848/nacos → 服务管理 → 服务列表
```

## 关键配置说明

### 各服务 application.yml（已精简）

公共段（sentinel/tenant/data-permission）已从各服务 yml 移除，统一放 Nacos 共享配置
（`deploy/nacos/ypbin-common.yaml`）或由各服务默认值兜底。各服务 yml 只保留：

- `server.port`：服务端口
- `spring.application.name`：服务名（Nacos 注册名，与 `@FeignClient(name=...)` 对应）
- `spring.cloud.nacos.server-addr`：Nacos 地址（`${NACOS_ADDR:localhost:8848}`）
- `ypbin.*`：业务开关（auth 的 sa-token/security excludes、system 的 tenant/data-permission 等）

### 网关路由

```yaml
spring.cloud.gateway.server.webflux.routes:
  - id: auth    uri: lb://ypbin-auth    predicates: Path=/auth/**,/captcha/**,/user/**,/open-api/**
  - id: system  uri: lb://ypbin-system  predicates: Path=/system/**
  - id: ai      uri: lb://ypbin-ai      predicates: Path=/ai/**,/share/**,/widget/**
```

白名单路径（无需登录）：`/auth/login`、`/captcha/**`、`/open-api/**` 等，见 gateway yml `ypbin.gateway.auth.whitelist`。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `NACOS_ADDR` | `localhost:8848` | Nacos 地址 |
| `SENTINEL_ADDR` | `localhost:8858` | Sentinel 控制台地址 |
| `DB_HOST/DB_PORT/DB_NAME` | `localhost/3306/ypbin_admin` | 共享数据库（system 服务用） |
| `DB_USER/DB_PASSWORD` | `root/空` | 数据库凭据 |
| `REDIS_HOST/REDIS_PORT` | `localhost/6379` | Redis |
| `MYSQL_ROOT_PASSWORD` | — | compose 内建 MySQL 密码（必填） |

## 常见问题

1. **服务起不来，报 Nacos 连接失败** → 确认 `NACOS_ADDR` 可达；本地开发可先起 Nacos 容器
2. **登录返回 401** → 网关白名单未包含该路径，或 token 过期；检查 gateway yml `whitelist`
3. **auth 报"系统服务暂不可用"** → `ypbin-system` 未注册到 Nacos；检查 system 服务日志
4. **接口 403** → 当前用户非平台用户（`@PlatformAccess` 限制）或权限码不足
5. **改配置不生效** → 共享配置在 Nacos 修改后，服务需 `refresh`（`spring.config.import` 支持自动刷新）或重启

## 单体版 vs 微服务版

| 维度 | 单体版（boot 分支） | 微服务版（main） |
|---|---|---|
| 架构 | 单应用（8080） | 网关 + 5 服务 |
| 注册中心 | 无 | Nacos |
| 服务调用 | 本地 Bean | OpenFeign + 身份头 |
| 当前用户 | `UserContext`（sa-token 会话） | `IdentityContext`（身份头） |
| 部署 | docker-compose（单容器） | docker-compose.yml（多容器） |
| 数据 | 单库单应用 | 单库多应用（system 服务独享写，auth/ai 经 Feign） |
