<div align="center">

# 🛡️ ypbin-admin

**企业级后台管理系统 · Enterprise Admin Platform**

> 基于 ypbin-starter 组装的生产就绪后台：RBAC、多租户、数据权限、任务调度、消息推送、License 商业授权、AI 对话，开箱即用。

**Java 21 · Spring Boot 4.1 · Sa-Token · MyBatis-Plus · Vue 3 · Ant Design Vue**

[![CI](https://github.com/wenbin-wb/ypbin-admin/actions/workflows/ci.yml/badge.svg)](https://github.com/wenbin-wb/ypbin-admin/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/Coverage-32%20Tests%20Passed-brightgreen.svg)](https://github.com/wenbin-wb/ypbin-admin/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
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

## 🧭 目录

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
| RBAC 权限模型 | 用户 / 角色 / 部门 / 岗位 / 菜单，按钮级鉴权 |
| 数据权限 | 注解驱动，按数据范围隔离（全部 / 本部门 / 本人等） |
| 多租户 | 行级租户隔离、租户管理与权限模板 |
| 审计日志 | 操作日志、登录日志、在线用户管理 |

### 🔐 认证与安全

| 能力 | 说明 |
|---|---|
| 多方式登录 | 账密 + 行为验证码、短信登录、第三方登录 |
| 登录防护 | 密码错误锁定、有效期策略、会话踢出 |
| 商业授权 | License 签发、双人审批、国密加密、授权码/授权文件交付（[使用说明书](LICENSE-USAGE.md)） |
| 接口安全 | 接口签名防重放、字段加密、数据脱敏 |

### 📨 消息与任务

| 能力 | 说明 |
|---|---|
| 消息中心 | 站内信 SSE 实时推送、未读提醒 |
| 通知公告 | 富文本编辑、定时发布 |
| 任务调度 | 动态定时任务（Cron）、执行日志、失败重试 |

### 🤖 AI 增强

| 能力 | 说明 |
|---|---|
| AI 对话 | 模型配置表驱动（OpenAI 兼容接口），模型地址/密钥/型号后台配置，不写死在 yml |
| 流式输出 | SSE 流式响应，多轮记忆持久化 |
| 用量统计 | Token 用量记录与查询 |

## 🧱 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21 · Spring Boot 4.1 · [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) · Sa-Token · MyBatis-Plus · MySQL · Redis |
| 前端 | Vue 3.5 · TypeScript · Ant Design Vue 4 · Vite · Pinia |
| 文档 | SpringDoc OpenAPI · Swagger UI |

## 🚀 快速开始

### 方式一：一键部署（Docker，推荐）

新服务器零配置一键安装（自动装依赖、构建、启动全部容器）：

```bash
bash <(curl -fsSL https://raw.githubusercontent.com/wenbin-wb/ypbin-admin/main/deploy/install.sh)
```

交互模式会询问：操作模式（完整部署 / 只更新后端 / 只更新前端 / 手动上传前端包）、端口（默认 MySQL 3307 / Redis 6380 / 后端 8080 / 前端 18080）、部署目录等；加 `-y` 全自动跳过所有询问（CI / 无头环境）。详细见 [部署文档](https://ypbin.cn/guide/admin/deployment)。

### 方式二：本地开发

```bash
# 后端（默认 8080，Flyway 自动建表灌种子；连接信息用环境变量覆盖）
mvn -f ypbin-admin-server/pom.xml spring-boot:run
```

接口文档为 `http://localhost:8080/swagger-ui/index.html`。首次部署需通过 Bootstrap 环境变量（`ADMIN_BOOTSTRAP_ENABLED` / `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD`）创建平台管理员，成功后关闭 Bootstrap。开发种子数据预置 `admin / admin123` 可直接登录，仅供本地调试。

前端启动见 [ypbin-admin-ui](https://github.com/wenbin-wb/ypbin-admin-ui)。

## 📁 项目结构

```
ypbin-admin-common    公共层：常量、starter 扩展点实现、基础配置
ypbin-admin-system    业务层：实体 / mapper / service / controller
ypbin-admin-server    启动层：主类、配置、数据库迁移（Flyway）
```

## 📚 文档

| 主题 | 链接 |
|---|---|
| 完整文档 | [ypbin.cn/guide/admin](https://ypbin.cn/guide/admin/) |
| 接口契约 | [ypbin.cn/guide/admin/api](https://ypbin.cn/guide/admin/api) |
| 架构与集成 | [ypbin.cn/guide/admin/architecture](https://ypbin.cn/guide/admin/architecture) |
| 部署指南 | [ypbin.cn/guide/admin/deployment](https://ypbin.cn/guide/admin/deployment) |
| AI 对话能力 | [ypbin.cn/guide/admin/ai](https://ypbin.cn/guide/admin/ai) |

## 🗺️ 路线图

- [x] 组织权限与 RBAC
- [x] 多租户与数据权限
- [x] 消息中心（SSE 推送）
- [x] AI 对话（配置驱动 + 流式输出）
- [x] License 商业授权
- [ ] 消息推送链路深度完善（邮件 / 短信）
- [ ] 用户中心打磨（重置密码、头像、通知偏好）

## 🤝 贡献指南

项目仍在持续迭代，欢迎：

- 提 [Issue](https://github.com/wenbin-wb/ypbin-admin/issues) —— bug、疑问、吐槽都行
- 提 [PR](https://github.com/wenbin-wb/ypbin-admin/pulls) 或建议 —— 有更好的做法，一起把它做对
- ⭐ Star / Fork —— 拿去改成你自己的项目，正是它存在的意义

## 📄 许可证

[Apache License 2.0](LICENSE)

Copyright © 2026-present wenbin
