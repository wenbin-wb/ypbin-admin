<div align="center">

# 🛡️ ypbin-admin

**企业级后台管理系统 · Enterprise Admin Platform**

> 基于 ypbin-starter 组装的生产就绪后台：RBAC、多租户、数据权限、空间管理、安全防护、任务调度、消息推送、License 商业授权、AI 知识库与对话，开箱即用。

**Java 21 · Spring Boot 4.1 · Spring Cloud Alibaba · Sa-Token · MyBatis-Plus · Vue 3 · Ant Design Vue**

[![CI](https://github.com/wenbin-wb/ypbin-admin/actions/workflows/ci.yml/badge.svg)](https://github.com/wenbin-wb/ypbin-admin/actions/workflows/ci.yml)
[![Tests](https://img.shields.io/badge/Tests-Passing-brightgreen.svg)](https://github.com/wenbin-wb/ypbin-admin/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](https://vuejs.org/)

[在线体验](https://admin.ypbin.cn) · [文档](https://ypbin.cn/guide/admin/) · [接口契约](https://ypbin.cn/guide/admin/api) · [架构](https://ypbin.cn/guide/admin/architecture)

</div>

---

## ✨ 界面预览

<div align="center">

![运行概览](https://ypbin.cn/screenshots/admin-ui/dashboard.png)
*运行概览 Dashboard · 实时运营分析与指标监控*

| 🤖 AI 智能对话工作台 | 👥 用户与组织架构 |
|---|---|
| ![AI 智能对话](https://ypbin.cn/screenshots/admin-ui/ai-chat.png) | ![用户组织管理](https://ypbin.cn/screenshots/admin-ui/users.png) |

| 🔑 角色与权限分配 | 🧭 动态路由菜单 |
|---|---|
| ![角色权限分配](https://ypbin.cn/screenshots/admin-ui/roles.png) | ![动态菜单管理](https://ypbin.cn/screenshots/admin-ui/menus.png) |

| 📜 商业授权 License | ⏰ 动态定时任务 |
|---|---|
| ![商业授权管理](https://ypbin.cn/screenshots/admin-ui/licenses.png) | ![定时任务调度](https://ypbin.cn/screenshots/admin-ui/jobs.png) |

| 🛡️ 安全审计操作日志 | 🚪 企业级登录入口 |
|---|---|
| ![安全审计日志](https://ypbin.cn/screenshots/admin-ui/logs.png) | ![企业登录入口](https://ypbin.cn/screenshots/admin-ui/login.png) |

</div>

> 📸 截图由线上真实运行实例（https://admin.ypbin.cn）自动采集，更多见 [截图清单](https://ypbin.cn/screenshots/admin-ui/manifest.json)。

## 📌 项目简介

前后端分离的企业级后台管理系统。后端基于 [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) 构建，专注业务实现；前端见 [ypbin-admin-ui](https://github.com/wenbin-wb/ypbin-admin-ui)。

统一响应、鉴权会话、多租户、数据权限、缓存、任务调度、消息推送这些"每个项目都要重写一遍"的系统级能力，全部沉在 starter 里做对做透。**admin 只干一件事——把它们接上业务，然后专心写业务。**

> 🚀 新项目从这里起步，第一天就站在生产就绪的地基上。

## 🌿 分支说明

本仓库同时维护两种部署形态，前端共用 [ypbin-admin-ui](https://github.com/wenbin-wb/ypbin-admin-ui)：

| 分支 | 形态 | 主要模块 | 说明 |
|---|---|---|---|
| `main` | 微服务版 | `ypbin-gateway` / `ypbin-auth` / `ypbin-service/{system,ai}` / `ypbin-common` | 主推形态；Spring Cloud Alibaba（Nacos / Sentinel / OpenFeign）+ XXL-JOB，配置集中在 Nacos |
| `boot` | 单体版 | `ypbin-admin-system` / `ypbin-admin-server` | 单进程部署，Flyway 管理建表与种子；适合小规模/私有化 |
| `boot-miniapp` | 单体 + 小程序 | 同 `boot` + 小程序业务模块 | 在 `boot` 基础上增量维护 |
| `feature/miniapp-backend` | 微服务小程序线 | 同 `main` + `ypbin-service/miniapp` | 按里程碑合并 `main` |

> 两种形态的启动/部署方式不同：`main` 见下方「本地开发（微服务版）」；`feature/miniapp-backend` 额外包含 `ypbin-miniapp` 服务（端口 18084）；`boot` / `boot-miniapp` 使用各自分支的 `deploy/install.sh`，无需 Nacos / Sentinel。

## 🧭 目录

- [分支说明](#-分支说明)
- [功能特性](#-功能特性)
- [技术栈](#-技术栈)
- [快速开始](#-快速开始)
- [项目结构](#-项目结构)
- [文档](#-文档)
- [路线图](#-路线图)
- [贡献指南](#-贡献指南)
- [许可证](#-许可证)

## ⚡ 功能特性

### 🏛️ 组织与权限

| 能力 | 说明 |
|---|---|
| RBAC 权限模型 | 用户 / 角色 / 部门 / 岗位 / 菜单，按钮级权限码；权限模板按租户复用 |
| 数据权限 | 注解驱动（`@DataPermission`），多角色数据范围取并集，读/写路径同一口径 |
| 多租户 | 行级租户隔离、租户管理与权限模板 |
| 空间管理 | 空间树（全量 / 懒加载）、空间与业务对象多态关联、CSV 导入导出、跨服务只读契约 |
| 审计日志 | 操作日志、登录日志、在线用户，支持导出与趋势统计 |

### 🔐 认证与安全

| 能力 | 说明 |
|---|---|
| 多方式登录 | 账密 + 行为验证码、短信登录、第三方 OAuth 登录与绑定 / 解绑 |
| 登录防护 | 密码错误锁定、登录客户端管理、会话踢出、平台超管强制首登改密 |
| 访问黑名单 | 手动拉黑与触发式自动封禁，规则下发网关，支持按 IP 封禁 / 解封 |
| 商业授权 | License 签发、双人审批、国密 SM2 / SM4、授权码 / 授权文件交付（[使用说明书](LICENSE-USAGE.md)） |
| 接口安全 | 接口签名防重放、字段加解密、数据脱敏、内部调用令牌校验 |

### 🧩 平台治理

| 能力 | 说明 |
|---|---|
| 应用与客户端 | 开放应用 AccessKey / SecretKey、登录客户端凭据与认证方式、密钥重置 |
| 认证模板 | 权限模板维护，租户开通时复用 |
| 系统配置 | 分组参数、批量更新、字典与字典项 |
| 文件与个人中心 | 文件上传 / 列表 / 删除、头像上传、资料与自助改密 |
| 仪表盘与埋点 | 运营概览、日志趋势；埋点事件目录、趋势 / 漏斗 / 留存与应用分布分析 |

### 📨 消息与任务

| 能力 | 说明 |
|---|---|
| 消息中心 | 站内信 SSE 实时推送、未读计数、批量已读 |
| 通知公告 | 富文本编辑、定时发布、撤回；发布后驱动站内信投递 |
| 任务调度 | XXL-JOB 分布式执行器：公告定时发布、埋点日聚合 |
| 消息通道 | 邮件测试、短信登录与通知通道（sms4j），通道配置动态注册 |

### 🤖 AI 增强

| 能力 | 说明 |
|---|---|
| AI 对话 | 模型配置表驱动（OpenAI 兼容接口），多轮会话、SSE 流式输出、重新生成、置顶 |
| 知识库（RAG） | PDF / Markdown / TXT 上传与批量导入、URL / Sitemap / RSS 导入、异步向量化、检索与重排测试 |
| 提示词与角色 | 提示词模板、对话角色维护与收藏 |
| 分享与嵌入 | 知识库分享链接、可嵌入的 `embed.js` 网页问答挂件 |
| 运营统计 | 对话量 / 热门问题 / 文档统计；Token 用量按日、按模型汇总，未回报的用量单独计为未知 |

## 🧱 技术栈

| 层 | 技术 |
|---|---|
| 运行时 | Java 21 · Spring Boot 4.1.2 · Spring Cloud Alibaba（Nacos / Sentinel / OpenFeign） |
| 基座 | [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) 4.4.0：统一响应、鉴权会话、多租户、数据权限、缓存、消息、任务、日志与埋点 |
| 数据与安全 | Sa-Token 1.46 · MyBatis-Plus · MySQL · Redis · XXL-JOB · sms4j |
| 前端 | Vue 3.5 · TypeScript · Ant Design Vue 4 · Vite · Pinia |
| 文档 | SpringDoc OpenAPI · Swagger UI |

## 🚀 快速开始

### 方式一：一键部署（Docker，推荐）

新服务器零配置一键安装（自动装依赖、构建、启动全部容器）：

```bash
# GitHub 可直连时
bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh)
# 国内服务器（GitHub 不可达，仓库源自动降级 Gitee 同名镜像；需先在 Gitee 建 ypbin-* 镜像并开启自动同步）
bash <(curl -fsSL https://gitee.com/wenbin_wb/ypbin-admin/raw/main/deploy/install.sh)
```

交互模式会询问：操作模式（完整部署 / 只更新后端 / 只更新前端 / 手动上传前端包）、端口（默认 MySQL 3306 / Redis 6379 / 网关 18080 / 前端 19000）、部署目录等；加 `-y` 全自动跳过所有询问（CI / 无头环境）。详细见 [部署文档](https://ypbin.cn/guide/admin/deployment)。

### 方式二：本地开发（微服务版）

前置：JDK 21、Maven 3.9+，以及可用的 MySQL / Redis / Nacos。

```bash
# 1. 起基础设施（也可直接用 deploy/install.sh 的 Docker 模式一键完成）
cd deploy
cp .env.example .env          # 必须替换 MYSQL_ROOT_PASSWORD、NACOS_AUTH_*、REDIS_PASSWORD 等占位值
docker compose up -d nacos redis mysql
cd ..

# 首次还需导入 deploy/sql/001~007（按需）与 deploy/nacos/*.yaml
#（见 docs/microservice-deployment.md 的「Nacos 配置中心」）；网关路由只在
# Nacos 模板里，缺 ypbin-gateway.yaml 时网关没有路由。
# 已有库升级：按变更顺序执行 deploy/sql/migration/*.sql（幂等）；
# 漏执行可能因缺列/缺字典值直接报错。

# 2. 安装内部模块到本地仓库
mvn -DskipTests install

# 3. 依次启动（IDE 中运行各 Application 类亦可）
mvn -f ypbin-service/ypbin-system/pom.xml spring-boot:run
mvn -f ypbin-auth/pom.xml spring-boot:run
mvn -f ypbin-service/ypbin-ai/pom.xml spring-boot:run
mvn -f ypbin-service/ypbin-miniapp/pom.xml spring-boot:run
mvn -f ypbin-gateway/pom.xml spring-boot:run
```

网关入口 `http://localhost:18080`。环境变量见根目录 `.env.example`（`run.bat` 会加载 `.env`，命令行启动请自行 export）。开发种子账号 `admin / pt5aQ5E6t8dkVkMp`，仅供本地调试，上线前必须清理；首次登录会被强制修改密码。使用 `deploy/install.sh` 部署时，超管初始口令由脚本随机生成并写入 `deploy/.env` 的 `SUPER_ADMIN_INIT_PASSWORD`。

构建与门禁：`mvn clean verify`（单元测试 + 架构门禁）。

前端启动见 [ypbin-admin-ui](https://github.com/wenbin-wb/ypbin-admin-ui)。

### 方式三：本地开发（单体版，`boot` / `boot-miniapp` 分支）

```bash
git checkout boot          # 或 boot-miniapp
mvn -DskipTests install
mvn -f ypbin-admin-server/pom.xml spring-boot:run
```

单体版无网关/Nacos/Sentinel：后端端口 `8080`，Flyway 启动时自动建表并写入种子数据；前端仍用 `pnpm dev:antd`，将 `VITE_GLOB_API_URL` 指向单体后端。

## 📁 项目结构

```
ypbin-common              公共层：常量、starter 扩展点实现、跨服务日志/埋点上报
ypbin-gateway             网关：路由、鉴权、内部身份头签发、黑名单规则下发
ypbin-auth                认证服务：登录、验证码、短信 / 第三方登录、会话
ypbin-service/            可部署业务服务：system（权限 / 空间 / 消息 / 日志 / 平台治理）、ai（模型 / 知识库 / 对话）、miniapp（小程序业务）
ypbin-service-api/        跨服务契约：Feign 接口 + 共享 DTO/实体视图（system-api / ai-api）
ypbin-architecture-tests  架构门禁：分层依赖、命名与源码约定、starter 版本契约
deploy/                   一键部署脚本、compose、Nacos 配置模板、建库与迁移脚本
tools/                    部署辅助工具（如 Nacos 仅线上键 diff）
```

> `boot` / `boot-miniapp` 为单体结构：`ypbin-admin-system`（业务 + 公共）、`ypbin-admin-server`（启动/装配 + Flyway 迁移），不使用 Nacos / Sentinel。

## 📚 文档

| 主题 | 链接 |
|---|---|
| 完整文档 | [ypbin.cn/guide/admin](https://ypbin.cn/guide/admin/) |
| 接口契约 | [ypbin.cn/guide/admin/api](https://ypbin.cn/guide/admin/api) |
| 架构与集成 | [ypbin.cn/guide/admin/architecture](https://ypbin.cn/guide/admin/architecture) |
| 部署指南 | [ypbin.cn/guide/admin/deployment](https://ypbin.cn/guide/admin/deployment) |
| AI 对话能力 | [ypbin.cn/guide/admin/ai](https://ypbin.cn/guide/admin/ai) |

仓库内文档：

| 主题 | 文件 |
|---|---|
| 更新日志 | [CHANGELOG.md](CHANGELOG.md) |
| 微服务部署 | [docs/microservice-deployment.md](docs/microservice-deployment.md) |
| 数据权限 | [docs/data-permission.md](docs/data-permission.md) |
| 权限灰度开启 | [docs/permission-rollout.md](docs/permission-rollout.md) |
| starter 4.4.0 升级回归 | [docs/starter-4.4.0-regression.md](docs/starter-4.4.0-regression.md) |

## 🗺️ 路线图

- [x] 组织权限与 RBAC
- [x] 多租户与数据权限
- [x] 空间管理与多态关联
- [x] 消息中心（SSE 推送）
- [x] 访问黑名单与自动封禁
- [x] AI 对话与知识库（RAG / 分享 / 嵌入）
- [x] License 商业授权
- [ ] 消息推送链路深度完善（邮件 / 短信通知）
- [ ] 用户中心打磨（通知偏好、安全设置）

## 🤝 贡献指南

欢迎提交 Issue 与 Pull Request，开发与提交规范见 [CONTRIBUTING.md](CONTRIBUTING.md)。

项目仍在持续迭代，欢迎：

- 提 [Issue](https://github.com/wenbin-wb/ypbin-admin/issues) —— bug、疑问、吐槽都行
- 提 [PR](https://github.com/wenbin-wb/ypbin-admin/pulls) 或建议 —— 有更好的做法，一起把它做对
- ⭐ Star / Fork —— 拿去改成你自己的项目，正是它存在的意义

## 📄 许可证

[Apache License 2.0](LICENSE)

Copyright © 2026-present wenbin
