# 贡献指南

欢迎参与 ypbin-admin 的贡献！本文件说明如何提交 Issue、编写代码与发起 Pull Request。

## 1. 提交 Issue

- **Bug 报告**：使用 [Bug 模板](.github/ISSUE_TEMPLATE/bug_report.yml)，说明复现步骤、期望/实际行为、相关日志与版本。
- **功能建议**：使用 [Feature 模板](.github/ISSUE_TEMPLATE/feature_request.yml)，说明使用场景。
- 提交前先搜索是否已有相同 Issue。

## 2. 环境准备

- JDK 21 + Maven 3.9+（构建命令 `mvn clean verify`）
- 依赖的 ypbin-starter 需先本地安装：`mvn -f <starter路径>/pom.xml install -DskipTests`
- admin 的 starter 依赖版本见根 pom 的 `ypbin-starter.version`（CI 会校验是否为最新 Release）

## 3. 开发规范

开发前必读仓库根目录 `AGENTS.md`（与 starter 同源规范），核心红线：

- 类级 Javadoc 带 `@author wenbin` + `@since`；顶部 Apache License 头
- 实体与 DTO 一律 `@Getter @Setter`，禁 `@Data`
- 禁内联全限定类名（FQCN）；禁魔法值（用 `system/enums` 下的枚举）
- Controller 极薄：仅路由分发，业务下沉 Service；写操作必须有权限校验 + 防重 + 操作日志
- 集合查无数据返回空集合；`@Transactional` 必须显式 `rollbackFor`
- 禁静默吞异常；`log.error` 必须传完整堆栈

## 4. 提交规范

- Conventional Commits：`type(scope): 描述`，如 `feat(system): 用户导入导出`、`fix(deploy): 修复 compose 端口映射`
- 不要添加 `Co-Authored-By` 尾注

## 5. 发起 Pull Request

1. 从 `main` 拉取最新代码，新建功能分支
2. 按 [PR 模板](.github/pull_request_template.md) 填写
3. 确保 CI 通过（构建 + 单测 + CodeQL + starter 版本防漂移校验）
4. 新功能必须有单元测试；接口变更需同步更新 ypbin-site 文档

## 6. 部署

- 部署脚本见 `deploy/`：`install.sh` 一键安装，配置经 `.env` 注入（勿硬编码密码）
- 生产服务器信息见仓库根 `ypbin-deploy-server.md`（谨慎操作）
