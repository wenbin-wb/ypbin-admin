<div align="center">

# ypbin-admin

**企业级后台管理系统**

Spring Boot · Sa-Token · MyBatis-Plus · Vue 3 · Ant Design Vue

[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](https://vuejs.org/)

</div>

---

## 预览

<p align="center">
  <img src="https://ypbin.cn/screenshots/admin-ui/dashboard.png" alt="运行概览" width="75%" />
</p>
<p align="center">
  <img src="https://ypbin.cn/screenshots/admin-ui/login.png" alt="登录页" width="55%" />
</p>

前后端分离的企业级后台管理系统。后端基于 [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) 构建，专注业务实现；前端见 [ypbin-admin-ui](https://github.com/wenbin-wb/ypbin-admin-ui)。

统一响应、鉴权会话、多租户、数据权限、缓存、任务调度、消息推送这些"每个项目都要重写一遍"的系统级能力，全部沉在 starter 里做对做透。admin 只干一件事——把它们接上业务，然后专心写业务。**新项目从这里起步，第一天就站在生产就绪的地基上。**

> 完整文档见 [ypbin.cn](https://ypbin.cn/guide/admin/) · [接口契约](https://ypbin.cn/guide/admin/api) · [架构与集成](https://ypbin.cn/guide/admin/architecture)

> 如果它帮你省下了搭脚手架的那几天，点个 Star 就是最好的回礼——也让更多人少走弯路。

## 功能

- **组织权限** — 用户、角色、部门、岗位、菜单，RBAC 与按钮级鉴权、数据权限
- **多租户** — 行级租户隔离、租户管理与权限模板
- **认证登录** — 账密 + 行为验证码、短信登录、第三方登录
- **消息中心** — 站内信 SSE 实时推送、通知公告（富文本、定时发布）
- **任务调度** — 动态定时任务与执行日志
- **商业授权** — License 授权签发、双人审批、国密加密、授权码/授权文件交付（[使用说明书](LICENSE-USAGE.md)）
- **系统运维** — 在线用户、操作日志、字典、参数配置、文件存储、接口文档

## 技术栈

| | |
|---|---|
| 后端 | Java 17 · Spring Boot 3.5 · [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) · Sa-Token · MyBatis-Plus · MySQL · Redis |
| 前端 | Vue 3.5 · TypeScript · Ant Design Vue 4 · Vite · Pinia |

## 快速开始

```bash
# 后端（默认 8080，Flyway 自动建表灌种子；连接信息用环境变量覆盖）
mvn -f ypbin-admin-server/pom.xml spring-boot:run
```

接口文档为 `http://localhost:8080/swagger-ui/index.html`。系统不提供默认可登录密码；首次部署需通过 `ADMIN_BOOTSTRAP_ENABLED=true`、`ADMIN_BOOTSTRAP_USERNAME` 和 `ADMIN_BOOTSTRAP_PASSWORD` 显式创建或启用平台管理员，成功后关闭 Bootstrap。开发种子数据预置 `admin / admin123` 可直接登录，仅供本地调试；生产部署请走上述 Bootstrap 流程。

前端启动见 [ypbin-admin-ui](https://github.com/wenbin-wb/ypbin-admin-ui)。

## 模块

```
ypbin-admin-common    公共层：常量、starter 扩展点实现、基础配置
ypbin-admin-system    业务层：实体 / mapper / service / controller
ypbin-admin-server    启动层：主类、配置、数据库迁移
```

## 参与进来

项目仍在持续迭代，功能与文档还在不断打磨。欢迎：

- 提 [Issue](https://github.com/wenbin-wb/ypbin-admin/issues) —— bug、疑问、吐槽都行
- 提 [PR](https://github.com/wenbin-wb/ypbin-admin/pulls) 或建议 —— 有更好的做法，一起把它做对
- Star / Fork —— 拿去改成你自己的项目，正是它存在的意义

用得顺手，或者哪里硌手，都欢迎告诉我。

## 许可证

[Apache License 2.0](LICENSE)

Copyright © 2026-present wenbin
