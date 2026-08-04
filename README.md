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

前后端分离的企业级后台管理系统。后端基于 [ypbin-starter](https://github.com/wenbin-wb/ypbin-starter) 构建，专注业务实现；前端见 [vue-vben-admin](https://github.com/wenbin-wb/vue-vben-admin)。

## 功能

- **组织权限** — 用户、角色、部门、岗位、菜单，RBAC 与按钮级鉴权、数据权限
- **多租户** — 行级租户隔离、租户管理与权限模板
- **认证登录** — 账密 + 行为验证码、短信登录、第三方登录
- **消息中心** — 站内信 SSE 实时推送、通知公告（富文本、定时发布）
- **任务调度** — 动态定时任务与执行日志
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

接口文档 `http://localhost:8080/swagger-ui/index.html`，初始账号 `admin / 123456`。

前端启动见 [vue-vben-admin](https://github.com/wenbin-wb/vue-vben-admin)。

## 模块

```
ypbin-admin-common    公共层：常量、starter 扩展点实现、基础配置
ypbin-admin-system    业务层：实体 / mapper / service / controller
ypbin-admin-server    启动层：主类、配置、数据库迁移
```

## 许可证

[Apache License 2.0](LICENSE)

Copyright © 2026-present wenbin
