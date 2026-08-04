<div align="center">

# ypbin-admin

**基于 [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) 的企业级后台管理系统**

后端只写业务，系统级能力全部来自 starter · 前端基于 vue-vben-admin · 前后端分离 · 开箱即用

[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](https://vuejs.org/)
[![Ant Design Vue](https://img.shields.io/badge/Ant%20Design%20Vue-4-1677ff.svg)](https://antdv.com/)
[![ypbin-starter](https://img.shields.io/badge/built%20on-ypbin--starter-blue.svg)](https://github.com/wenbin-wb/ypbin-starter)

[快速开始](#快速开始) · [功能特性](#功能特性) · [设计取舍](#设计取舍) · [与-ypbin-starter-的关系](#与-ypbin-starter-的关系) · [文档](#文档)

</div>

---

## 简介

`ypbin-admin` 是一套前后端分离的企业级后台管理系统：后端基于 [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) 构建，前端基于 [vue-vben-admin](https://github.com/vbenjs/vue-vben-admin)。

它的定位很明确——**只做业务，不造轮子**。统一响应、鉴权会话、多租户隔离、数据权限、缓存、任务调度、消息推送这些系统级能力全部由 starter 提供，admin 只负责把它们「接上数据库、连上前端」：实现 starter 的扩展点接口（Provider），落地 RBAC、字典、在线用户、定时任务等具体业务，并暴露 REST 接口给前端。

这样拆分的好处是职责边界一刀切干净：**升级 starter 不动业务表，迭代业务不碰系统基建**。admin 的每一个模块都是「starter 抽象能力 + 业务数据 + 前端页面」的三段式落地范例，可直接作为新项目的起点。

## 与 ypbin-starter 的关系

admin 与 starter 的分工，是「业务系统如何正确使用一套基建」的实践样本：

| 系统级能力 | 由谁提供 | admin 侧做了什么 |
|---|---|---|
| 统一响应 / 全局异常 / Long 转字符串 | starter（core/web/json） | 直接用，无需任何代码 |
| 鉴权与会话（登录/踢人/续期） | starter（security，Sa-Token） | 实现 `PermissionProvider` 提供权限码，接通注解鉴权 |
| 多租户行级隔离 | starter（extension-tenant） | 实现 `TenantProvider` 提供当前租户 ID |
| 数据权限（行级数据范围） | starter（extension-datapermission） | 实现 `DataScopeHandler` 拼数据范围 SQL |
| 字典翻译 / 引用翻译 | starter（json，`@DictText`/`@RefText`） | 实现 `DictProvider` / `RefTextProvider` 接数据库 |
| 登录客户端 / 密码策略动态配置 | starter（security） | 实现 `DbLoginClientProvider` / `DbPasswordPolicyProvider` |
| 定时任务调度 | starter（job） | 用 `@YpbinJob` 写任务体，实现执行日志落库监听 |
| 邮件 / 存储 / 开放应用配置后台化 | starter（messaging/storage/sign） | 实现对应 `*ConfigProvider` 从数据库读配置 |
| 操作日志 IP 归属地 | starter（log） | 实现 `Ip2regionLocationResolver` 接 ip2region |

> 核心原则：**系统级需求一律优先「用 starter 已有能力」或「实现 starter 扩展点」，不在 admin 里重造。** 若发现 starter 缺能力，反馈到 starter 改进，而非在 admin 侧绕开。这保证了基建的一致性与可复用性。

starter 的完整模块能力、设计决策与扩展点清单见 **[ypbin-starter →](https://github.com/wenbin-wb/ypbin-starter)**。

## 设计取舍

> 几个关键决策及其理由，解释 admin 为什么长这样。

- **admin 不碰系统级基建，哪怕「顺手就能写」。** RBAC 的权限校验、多租户隔离、数据权限过滤都能在业务代码里手写，但那样每个项目都要重写一遍、且容易写错。admin 一律通过实现 starter 扩展点落地——把「正确的做法」固化在基建层，业务层只提供数据源。
- **字段全链路同名，拒绝改名映射。** DB 列名 = 实体字段（驼峰）= 前端 JSON 字段，全程同名。要对齐前端就直接改 DB 列，不留旧名再转换。派生展示字段（如 `deptName`）由 starter 的序列化器按 `@RefText` 额外输出，不算改名。这消除了 DTO ↔ Entity ↔ VO 三层手工映射的心智负担与出错点。
- **异常统一 HTTP 200 + 业务码。** 所有业务异常走 `R` 结构、HTTP 恒 200、靠 `code` 区分，前端只需一套拦截逻辑。这是 starter 的约定，admin 抛 `BusinessException` 即可，全局异常处理已兜底。
- **前后端分离，各自独立仓库、独立部署。** 后端（本仓库）是纯 REST 服务；前端 [vue-vben-admin](https://github.com/wenbin-wb/vue-vben-admin) 独立构建。菜单与按钮权限由后端 `/menu/all` 动态下发，前端不内置路由表——权限模型的唯一事实源在后端。
- **菜单、字典、在线用户等表结构归 admin，不进 starter。** starter 只给运行时与扩展点，业务表与页面归 admin。升级 starter 不会动 admin 的数据，职责边界清晰可预期。

## 功能特性

- **系统管理**：用户、角色、部门、岗位、菜单（含按钮级权限码）、字典、参数配置。
- **RBAC 权限**：角色-菜单授权、按钮级鉴权、数据权限（按部门/自定义范围行级过滤）。
- **多租户**：行级租户隔离，租户管理 + 权限模板（租户可用菜单范围）。
- **认证登录**：账号密码 + 行为验证码、短信登录、第三方（社会化）登录。
- **消息中心**：站内信 + SSE 实时推送、通知公告（富文本、定时发布、多渠道通知）。
- **任务调度**：动态定时任务（注册/启停/改 cron/立即执行）+ 执行日志。
- **文件存储**：本地 / S3 兼容对象存储，头像与封面上传裁剪。
- **运维监控**：在线用户（踢人）、操作日志、登录日志、系统监控、接口文档（SpringDoc）。
- **个人中心**：基本信息、安全设置、改密、我的消息。

## 技术栈

**后端（本仓库）**

| 项 | 版本 / 选型 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.5.16 |
| 系统级基建 | [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter)（app-web + crud/tenant/datapermission/job/messaging/sign/social/captcha/storage） |
| 认证 | Sa-Token（由 starter security 提供） |
| ORM | MyBatis-Plus（由 starter data 提供） |
| 数据库 | MySQL 8 |
| 迁移 | Flyway |
| 缓存 | Redis |
| 短信 | SMS4J 3.3.5 |

**前端（[wenbin-wb/vue-vben-admin](https://github.com/wenbin-wb/vue-vben-admin)）**

| 项 | 版本 / 选型 |
|---|---|
| 框架 | Vue 3.5 + TypeScript |
| 基座 | vue-vben-admin 5.7（web-antd 应用） |
| UI | Ant Design Vue 4 |
| 构建 | Vite + pnpm + Turbo（monorepo） |
| 状态 | Pinia |

## 快速开始

### 前置条件

- JDK 17、Maven 3.9+
- MySQL 8、Redis
- Node.js 20+、pnpm（前端）
- **ypbin-starter**：admin 依赖 starter，需先在本地安装或从仓库获取（见下方说明）

### 1. 后端

```bash
# 1. 建库（默认库名 ypbin_admin，Flyway 会自动建表并灌入种子数据）
#    连接信息通过环境变量覆盖：DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD
#    Redis：REDIS_HOST/REDIS_PORT/REDIS_DB/REDIS_PASSWORD

# 2. 编译并启动（server 模块）
mvn -f ypbin-admin-server/pom.xml spring-boot:run
```

启动后后端监听 `http://localhost:8080`，接口文档见 `http://localhost:8080/swagger-ui/index.html`。

初始账号见 Flyway 种子数据（`ypbin-admin-server/src/main/resources/db/migration/V2__data.sql`）：超级管理员 `admin`，默认密码 `123456`。

### 2. 前端

前端在独立仓库 [wenbin-wb/vue-vben-admin](https://github.com/wenbin-wb/vue-vben-admin) 的 `web-antd` 应用：

```bash
pnpm install
pnpm dev:antd     # 开发服务默认 http://localhost:5666
```

> 关于 ypbin-starter 依赖：当前 admin 依赖 starter `1.1.0-SNAPSHOT`。starter 发布正式版后会切回 Central 上的 release 版本；在此之前需从 [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) 源码 `mvn clean install` 安装到本地 `.m2`。

## 项目结构

```
ypbin-admin/
├── ypbin-admin-common/    # 公共层：常量、starter 扩展点实现（Provider）、基础配置
├── ypbin-admin-system/    # 系统业务层：实体 / mapper / service / controller（各业务模块）
└── ypbin-admin-server/    # 启动层：主类、application.yml、Flyway 迁移脚本
```

## 文档

| 文档 | 内容 |
|---|---|
| [docs/STARTER-INTEGRATION.md](docs/STARTER-INTEGRATION.md) | admin 如何对接 starter：核心铁律、扩展点实现清单与要点 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 开发规范、模块划分、约定 |

## 本地构建

```bash
# 全量编译（需先具备 ypbin-starter 依赖）
mvn clean install

# 仅编译校验，不跑测试
mvn -DskipTests clean compile
```

## 许可证

基于 [Apache License 2.0](LICENSE) 开源，可自由用于商业项目。

前端基座 vue-vben-admin 遵循其 MIT 许可，改造部分见前端仓库说明。

Copyright © 2026-present wenbin
