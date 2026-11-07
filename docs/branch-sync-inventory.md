# 分支同步清单（main → boot / boot-miniapp / feature/miniapp-backend / iot）

> 生成日期：2026-11-07（基线 `origin/main` @ `73da93f`）。
> 用途：明确「admin main 上已落地的变更」与各下游分支/仓库的差值，作为回灌/合并的权威清单。
> 口径：以 main 的 **first-parent** 提交为单位（一个 PR 合并 = 一个功能单位；直接提交单独列出）。
> 复现命令见文末 §8。

## 1. 结论

| 目标 | 定位 | 与 main 的差距 | 同步方式 | 结论 |
|---|---|---|---|---|
| `boot` | 单体产品线（`ypbin-admin-system` + `ypbin-admin-server`） | main 领先 **300** 个提交（271 个 first-parent），boot 独有 49 个 | **不能整体合并**（架构不同）；按功能逐个回灌 | 需回灌；已有 2026-09～11 的连续回灌记录（§4） |
| `boot-miniapp` | 单体 + 小程序 | main 领先 300，分支独有 62 | 同上 | 需回灌；比 boot 多 13 个自有提交，需先对齐两条单体线的差异 |
| `feature/miniapp-backend` | 微服务（与 main 同构） | main 领先 **96**（67 个 first-parent），分支独有 44 | **可直接 merge main** | 可合并；dry-run 有 27 处冲突（§5） |
| `iot`（独立仓 `ypbin-iot`） | admin 的 fork（微服务） | 已用 PR #318 合并 upstream 至 `69d5850`；其后仍缺 **#130 + 4.4.1 同步/回退** | `git merge upstream/main`（SYNC.md 纪律） | 待 #318、#319 合并后补 #130 |

## 2. 风险分级（回灌优先级）

- **P0 安全**：访问黑名单管理端与计数（`ab54095` `49826ca` `b953dca`）、客户端 IP 口径统一（`#130`：登录/分享入口 + `trusted-proxies`）、平台超管强制首登改密、CORS 安全默认。
- **P1 契约**：空间域（树/关联/统计/导入导出）、公告详情与在线用户分页、AI 对话入参、站内信/邮件端点、`@PlatformAccess`/数据权限修复。
- **P2 质量**：P2 审查整改系列、部署脚本漂移、依赖升级（dependabot）。


## 3. main 侧全量变更清单（271 项，按月份倒序）

> 这是「需要被下游吸收」的权威清单。`boot`/`boot-miniapp` 逐项判断是否已按单体形态回灌（对照 §4 的回灌提交）；
> `feature/miniapp-backend` 直接合并即可；`iot` 按 §6。


#### 2026-11

- `73da93f` Merge pull request #135 from wenbin-wb/claude/revert-starter-4.4.1
- `690154f` chore(deps): ypbin-starter 升级至 v4.4.1
- `52d5602` Merge pull request #130 from wenbin-wb/claude/admin-ip-alignment
- `69d5850` chore(deps): ypbin-starter 升级至 v4.4.0
- `b953dca` fix(security): 防止 auth 自动封禁网关地址
- `49826ca` fix(security): 启用 auth 黑名单计数并收口规则下发
- `ab54095` feat(security): 访问黑名单管理端（微服务线）
- `67032c6` chore(deps): ypbin-starter 升级至 v4.3.0
- `8d3cb10` Merge pull request #93 from wenbin-wb/dependabot/maven/spring-7e727bb71d
- `bf144d7` Merge pull request #94 from wenbin-wb/dependabot/maven/org.apache.maven.plugins-maven-surefire-plugin-3.6.1
- `a8e64df` Merge pull request #110 from wenbin-wb/claude/p2-c-split
- `8b6051f` Merge pull request #108 from wenbin-wb/claude/p2-c-split
- `1cf6934` Merge pull request #106 from wenbin-wb/claude/p2-c-split
- `0202650` Merge pull request #104 from wenbin-wb/claude/p2-c-split
- `ad87ac2` Merge pull request #103 from wenbin-wb/claude/p2-c-split
- `3db029c` Merge pull request #102 from wenbin-wb/claude/p2-cleanup
- `099a92a` Merge pull request #101 from wenbin-wb/claude/p2-batch3b
- `9f60eff` Merge pull request #100 from wenbin-wb/claude/p2-batch3a
- `4186bfe` Merge pull request #99 from wenbin-wb/claude/p2-batch2
- `1bcd5d3` Merge pull request #98 from wenbin-wb/claude/p2-fixes
- `f568ab1` Merge pull request #97 from wenbin-wb/claude/project-code-review-257442

#### 2026-10

- `d369aa3` docs(deploy): 修正微服务版本地启动与部署说明
- `89925d6` fix: 两轮代码审查整改（租户边界、并发一致性、重复实现收敛）
- `337d42b` refactor(system): 空间路径拼装收敛为一处（消除同一算法的两份实现）
- `85e2f6f` feat(system): 补齐站内信/邮件端点 + 门禁识别 IService 隐式调用
- `35f1662` fix(system): 独立代码审查发现的 8 项缺陷（含 3 项跨租户/数据正确性）
- `4429efa` fix(system): 空间导入导出的 4 项缺陷（数据库异常/静默挂根/N+1/重复造轮子）
- `d42a94d` feat(system): 空间 CSV 导入导出（首次建站的批量录入）
- `b1c90ce` feat(system): 空间对象数量统计的对外端点（前端「对象数」列）
- `e078fb8` feat(system): 补空间「批量反查」与「整体同步」跨服务契约
- `49a7810` feat(system): 空间视角取对象 ID + 按空间统计（IoT 设备资产管理依赖）
- `ae0f455` feat(system): 新增「取空间子树 ID」契约（业务侧按空间筛选的支撑）
- `eac9520` feat(system): 空间树懒加载（万级空间的可扩展性）
- `a042ec7` feat(system): 岗位支持「工作地点」（空间），createPost 返回新 ID
- `aea0b99` feat(system): 空间关联支持批量反查（列表页 N+1 的解法）
- `7c0b0af` test(system): 空间域 47 例 + 2 条门禁（含变异验证）
- `a1b6ffa` feat(system): 部门/用户接入空间——删除时清理关联，部门创建返回 ID
- `699b688` feat(system-api): 空间域跨服务契约（6 个内部端点 + 2 个只读 DTO）
- `b7212ef` feat(system): 空间管理服务层（树形 CRUD + 多态关联 + 字典选项端点）
- `e718a21` feat(system): 空间管理基础主数据（DDL + 迁移 + 回滚）
- `5eb24b5` feat(deploy): 同步 iot#304 的 live-only 键检查（工具 + install.sh + 门禁 + CI + 文档）
- `6ee8341` test(docs): 补 auth 必须显式关闭 identity 的门禁与文档（issue #92）
- `2480a1d` fix(deploy): auth 显式关闭身份头账号体系（identity.enabled=false），修登录 401
- `4eda841` fix(deploy): xxl-job token 占位符改裸写法 + install.sh 容忍后缀 + 渲染匹配门禁（同步 iot 回执 r2 §3.2）
- `c78806a` fix(deploy): system 的 xxl-job access-token 由空串改为必填占位符（+提供方），并加部署配置门禁
- `de9243a` chore(deps): ypbin-starter 升级至 v4.2.1
- `b6acff8` fix(deploy): auth 显式声明 security.interceptor=true（同步 iot #302）
- `6d85f35` test(ai): 缓存未命中桩改到 getOrLoad 三参重载
- `e8701e4` refactor(system-api): SysCache 改用 getOrLoad 无 Class 重载，根治 4 处双重强转
- `caf1215` refactor(auth): LoginSupport 改用 starter 的 UserAgentFormatter（删私有重复实现）
- `81c7ecb` chore(deps): ypbin-starter 升级至 v4.2.0
- `1f390dc` merge: 部门树叶子 children 归一（反哺自 iot 仓的缺陷报告）
- `fd4dc07` merge: 消费 starter 4.1.0（#100/#101/#103/#106/#107 宿主侧迁移）
- `e26d6a7` chore(deps): ypbin-starter 升级至 v4.1.0
- `d3694ab` merge: #87 补齐敏感键判据缺口 + 掩码写路径两道守卫
- `9a130c8` merge: #86/#87/#88/#89 修复（注释事实、脱敏口径、小缺陷、testConnection 审计）+ starter#105 第一步
- `607b66a` fix(deploy): ypbin-auth 补齐 Feign 身份来源校验配置对齐 system/ai，common 补齐说明注释 (#85)
- `ada9f9b` refactor(admin): 跟随 starter 4.0.0 下沉（删自建守卫/租户/Feign 注入）+ 修 issue #78 (#84)
- `50b23d1` chore(deps): ypbin-starter 升级至 v4.0.0
- `cae5097` chore(deps): ypbin-starter 升级至 v3.9.1
- `ca903a8` chore(deps): ypbin-starter 升级至 v3.9.0
- `922d0d5` chore(deps): ypbin-starter 升级至 v3.7.0

#### 2026-09

- `7bc3bdd` chore(deps): ypbin-starter 升级至 v3.6.0
- `5e06b2a` fix(deploy): 配置导入与部署凭据卫生（反馈 UP-6~UP-9） (#77)
- `c22de10` chore(deps-dev): bump com.tngtech.archunit:archunit-junit5 (#76)
- `91a801e` chore(deps): ypbin-starter 升级至 v3.5.0
- `f444dce` fix(system): 引用文本翻译容错——脏主键跳过并记日志，空集合不进 SQL（CodeQL uncaught-number-format-exception） (#73)
- `7169e47` test(system-api): 同步断言硬化与 Javadoc 对齐到 main（保持与小程序分支逐字一致） (#72)
- `cfdd8f7` fix(system-api): 同步端侧参数必填与部署文档修订到 main（保持与小程序分支逐字一致） (#71)
- `0b6d36f` fix(system-api): 同步通用端点的行为修复与「禁空 catch」硬化到 main（保持与小程序分支逐字一致） (#69)
- `6b3419c` feat(system-api): 引入通用用户端点与「禁空 catch」门禁（消除与小程序分支的基础模块分叉） (#68)
- `dafda5e` fix(arch): 投影类移出 api 模块并订正过期注释（独立复核发现） (#62)
- `5cf56eb` refactor(auth): 收窄跨服务契约为只读视图，使 auth 不再传递依赖 starter-data (#61)
- `9cbd019` fix(deploy): 诊断不再猜原因（回显实际证据 + 可执行自查），并修正与真因不符的文案 (#60)
- `def8ab9` fix(deploy): 诊断不再猜原因（回显实际证据 + 可执行自查），并修正与真因不符的文案 (#59)
- `eb6d729` fix(deploy): REGISTRY_PREFIX 尾斜杠归一化提升为全局，并同步文档 (#57)
- `0fe2495` fix(deploy): pnpm 起不来时精确报因（缺系统库 vs 网络超时），不再猜「大包拉取超时」 (#58)
- `389e553` fix(deploy): 显式 REGISTRY_PREFIX 缺尾斜杠时归一化补 / (#56)
- `326bdc1` feat(deploy): install.sh 起基础设施不再探测镜像加速源，只走机器默认 (#55)
- `63ea7d0` fix(deploy): 删除 install.sh 中多余的 `reg prefix` 声明行（我上次修错留下的） (#54)
- `4b08eff` fix(deploy): 修 install.sh 在「Docker Hub 不可达走镜像回退」路径上的 `local` 函数外使用 (#53)
- `2e5cef2` fix(arch): 门禁不再依赖打包形态 + 夹具自包含（修部署中断与三条规则静默退化） (#52)
- `b960bc0` fix(deploy): 首次全新部署自动生成 AI_MODEL_SECRET_KEY
- `f59fb7f` feat(tracking): 新增宿主 project 层事件目录，并补 service/impl 禁依赖 provider 的架构规则
- `a432204` refactor(system): 数据范围解析抽为 service/support 共享能力，provider 仅保留端口适配
- `6084c0f` fix(system): 在线用户姓名回填改全局并补写路径 deptId 数据范围校验
- `01b0f05` fix(system): 修复内部端点租户忽略、手机号全局查重与数据范围报错指引
- `d0bcb3e` docs(changelog): 补回被上一提交误删的修复条目（用户名查重移出数据范围）
- `d271c5f` docs(changelog): 订正两处陈旧条目（用户名查重已修复 / starter 版本表述）
- `6e3f9b3` feat(system,common): 补齐数据范围/敏感词/日志客户端三处宿主端口与在线用户分页
- `1123438` docs(permission): 更正"system/ai 缺 sa-token-jackson3"的错误论断（dependency:tree 实测）
- `249da20` feat(ai,system): AI 用量落库并补齐 outcome/error_message；用户名查重移出数据范围
- `256782b` chore(deps): ypbin-starter 升级至 v3.4.0
- `c2c85b6` fix(system): 菜单权限码变更后的缓存失效改用批量删除
- `35aa6f4` feat(security): 补三处 PermissionProvider 与权限缓存批量失效，并写入灰度开启手册
- `c9568c0` fix(ai): 补 PlatformUserChecker 实现，修复 AI 接口 @PlatformAccess 恒 403
- `30fc686` fix(arch): 修 CI 场景自检的清理残留与扫描范围盲区（复核意见）
- `377395f` fix(arch): 嵌套仓名单只保留实测存在的兄弟仓名
- `e5d731a` fix(arch): 扫描范围收敛到本仓模块，修掉「本地绿、CI 红」并补 @Transactional 类级检查
- `9c71983` fix(arch): 修掉批量 SQL 写入 NULL 逻辑删除列等复核发现的 P0
- `2886442` fix(arch): 修掉门禁暴露的 9 条既有违规；并按复核意见收口埋点上报
- `e537426` fix(auth): 埋点上报兜住 Error 并先取令牌再上报，补 TrackEvent 线格式测试
- `1c54fce` test(arch): 新增 ypbin-architecture-tests 架构门禁，把 admin 铁律变成构建失败
- `6035596` feat(auth): 登录/登出埋点真正上报，补上全仓缺失的发送方
- `d12e640` docs(log): 补明 3.3.0 的 LogEventListener 不带堆栈，故堆栈由本类与上报侧兜住
- `2314b48` feat(log): 登录/AI 日志经内部端点落 sys_log，并引入 async 使落库脱离业务线程
- `d11d3b3` feat(tracking): 埋点管理改回顶级菜单
- `1b560ef` fix(system,auth): 接通 @DictText 字典翻译与操作日志落库，并记录登录终端信息
- `1d08f87` fix(auth): ypbin-common 接入 sa-token Redis 会话存储，修「在线用户列表恒为空」
- `5b4e086` feat(tracking): 补漏斗分析与留存分析两个菜单入口
- `317c6e9` fix(tracking): 修正留存摘要半截日口径并支持聚合手工回填
- `49578aa` fix(json): 补齐微服务谱系缺失的 RefText provider，让 @RefText 字段真正产出 xxxName
- `c520463` feat(tracking): 埋点漏斗与留存后端（三张聚合表 + XXL-Job 聚合任务 + 两个查询接口）
- `fac3051` fix(tracking): 菜单挂到系统监控下、修正不存在的图标、补上漏掉的分析页菜单
- `23a0c5e` fix(deploy): 校验前端产物在容器内真的可见（bind mount 陷阱），必要时自愈重建
- `7ca4b73` feat(deploy): 前端构建按“产物是否比源码新”自动判断，并新增 REBUILD_FRONTEND 开关
- `1245307` feat(tracking): 新增事件排行与应用分布两个聚合接口（分析页数据源）
- `a831498` feat(tracking): 新增「埋点管理」菜单（事件列表 / 事件目录）
- `42f53eb` chore(deps): ypbin-starter 升级至 v3.3.0
- `07667a1` fix(deploy): 内部端口可一键收窄 + 服务依赖改为等健康 + [7/7] 校验全部容器
- `8723592` feat(tracking): 补齐埋点登录身份提供者实现
- `76ab662` fix(deploy): 部署完成横幅补齐前端/XXL-Job 地址与数据库密码位置
- `f542554` fix(deploy): 磁盘空间预检 + compose 失败按真因分类（磁盘不足/端口占用）
- `1a355b4` fix(deploy): 前端依赖安装放宽 pnpm 拉取超时并自动重试一次
- `e3133bc` fix(deploy): MySQL 初始化前做认证探测，密码与数据卷不一致时就地失败
- `b2b7b57` fix(deploy): 修正 AI_MODEL_SECRET_KEY 的生成命令（原提示会被自己的校验器拒绝）
- `730cf28` fix(deploy): 基础设施启动失败按真因分类，不再一律甩锅给镜像源
- `05d0f98` fix(deploy): 海外服务器优先用 Docker Hub 官方源（原来全新机器上从不尝试官方）
- `918af8b` fix(deploy): starter 构建改用与 admin 依赖一致的 tag，并在版本不符时就地失败
- `dc025bc` feat(tracking): 埋点事件落库与查询接口
- `99afdbd` chore(deps): ypbin-starter 升级至 v3.2.0
- `b16449b` fix(deploy): 补齐所有 curl 硬超时并修复脚本自下载/降级提示
- `3466cc3` chore(ci): 三个分支统一触发 CI/CodeQL，并对齐 Action 版本
- `6bfb9cf` fix: 修复上一提交丢失的 import（InternalTokenConstants）
- `a9fe679` style: 整理 4 个文件的 import 顺序（与同文件其余部分保持一致）
- `a7550eb` chore(deps): 升级 ypbin-starter 3.0.0 → 3.1.0，移除临时 LogSanitizer
- `795d770` chore: 补 .gitattributes 与 .editorconfig
- `06fe7cd` chore(ci): Dependabot 新增 analysis 分组（Error Prone + NullAway + JSpecify）
- `15dd099` fix(quality): 按代码扫描建议清理 admin 侧剩余告警
- `cfbabf8` fix(security): admin 侧日志注入加固（7 处）
- `818f5e3` chore(deps): bump org.projectlombok:lombok from 1.18.46 to 1.18.48
- `295aad7` chore(deps): bump the spring group across 1 directory with 4 updates
- `7be13d1` chore(deps-dev): bump org.apache.maven.plugins:maven-compiler-plugin from 3.13.0 to 3.16.0
- `db2f06b` chore(deps): bump org.jsoup:jsoup from 1.17.2 to 1.23.2
- `be920a2` chore(deps-dev): bump org.codehaus.mojo:flatten-maven-plugin from 1.6.0 to 1.8.0
- `724547f` chore(deps-dev): bump org.apache.maven.plugins:maven-surefire-plugin from 3.5.4 to 3.6.0
- `37f42ff` chore(deps): bump github/codeql-action from 3 to 4
- `569bf08` chore(deps): bump softprops/action-gh-release from 2 to 3
- `11d6c7d` fix(security): 修掉代码扫描发现的一处真实校验失效与三处泄露/健壮性问题
- `1196c41` fix(deploy): AI_MODEL_SECRET_KEY 校验补齐环境变量路径与长度前置校验
- `b773234` chore(deps): ypbin-starter 升级至 v3.0.0
- `90fe796` fix(ci): admin 依赖版本回退 2.2.3——版本漂移门禁要求与 starter 最新 Release 一致
- `bd1851d` feat(deploy)!: 对齐 starter 3.0.0 + 移除 AI 密钥弱默认 + .env.example 全占位化
- `46ba74f` feat(security): 网关身份头签名接入 + 租户 fail-closed + SSRF 防护与废弃 API 迁移
- `21a5013` feat(deploy): 分支部署实战增强——compose 项目名隔离/本地镜像直起/网络重叠自愈
- `0fe5d08` docs(deploy): 补分支部署/网络受限镜像/初始口令说明
- `f8062f3` feat(deploy): 支持 -b/--branch 与 --root——分支部署目录自动隔离
- `df744ac` fix(deploy): 镜像探活 curl 加 
- `686e219` fix(deploy): 镜像探活不用 -f——401 属 registry 正常未认证, 应判可达; 改状态码 200/301/302/401 判定
- `f0211e7` fix(deploy): 镜像加速前缀补尾斜杠 + 逐源 v2 探活 + 候选扩充
- `dbd6fc9` feat(deploy): 基础设施镜像官方源拉取失败自动尝试国内公共镜像加速前缀
- `0a86543` fix(deploy): 仓库源降级提示污染 REPO_BASE——resolve stdout 仅回 URL
- `b68a0cd` fix(deploy): Maven 安装适配国内——先启用 universe 组件再 apt，失败走阿里镜像二进制(Maven 3.9.9)
- `05d91ef` feat(deploy): 国内服务器 APT/docker-ce 源自愈——compose 插件自动安装失败时切阿里镜像源后重装
- `d2c4d81` fix(deploy): 缺 Docker Compose 插件时自动安装(apt docker-compose-plugin/v2 双兜底), 不再直接报错退出
- `7eeafa1` fix(deploy): Gitee 用户名 wenbin_wb + STARTER_VERSION 改从 admin pom 自动解析
- `01ef7b2` feat(deploy): 仓库源国内可用——GitHub 不可达自动降级 Gitee + Docker 镜像前缀
- `357a28a` chore(deps): ypbin-starter 升级至 v2.2.3
- `a5fd548` fix(review): 复审整改 + 升依赖 ypbin-starter 2.2.2
- `488cfdd` chore(deps): ypbin-starter 升级至 v2.2.2
- `541327d` perf(system/ai): 公告投递批量插入 + AI 用量统计 SQL 聚合
- `dd80092` fix(deploy): 补内部 Feign 凭证 INTERNAL_TOKEN 链路
- `d64669f` fix(security): 内部 Feign 端点令牌守卫 + 缓存一致性 + AI 越权/SSRF + auth 加固
- `c2c3e83` fix(deploy): 凭据 .env 化 + Redis 认证 + 修 Sentinel 死键
- `e2b636b` fix(build): pin maven-surefire-plugin 3.5.4 so tests run on any Maven
- `93ac380` chore(deps): main 升级 ypbin-starter 2.2.0 → 2.2.1（SSE 微服务身份头修复）
- `ef28ec2` fix(ci): auth 安全 excludes 改剥前缀后路径（服务实际收到 /login 等）
- `9962bd6` refactor(api): Controller 去业务域前缀 + 网关 StripPrefix 统一剥短名
- `aedc5ea` refactor(api): 路由收敛为服务唯一段——auth/auth、system/system、ai/ai
- `f7e1f61` fix(gateway): 补 SSE 路由与订阅白名单——/ypbin/** 转发 system
- `22511bb` deploy(compose): 服务固定 IP（ypbin-net 子网），根治容器重建 IP 漂移致 nginx 502
- `53b91b1` feat(auth): 接通登录后菜单/路由/权限链路（auth→system Feign）
- `248e899` fix(ci): sync-starter-version 拉取 starter release 带 GITHUB_TOKEN 认证
- `35a2341` chore(deps): main 升级 ypbin-starter 2.1.1 → 2.2.0（新增 xxljob 接入壳）
- `fabe2b7` fix(xxl-job): admin 显式置空 accessToken，对齐执行器空 token
- `df1c187` fix(xxl-job): 启用 ypbin-system 执行器，admin 地址修正为根路径
- `f09274e` fix(deploy): 复用已有 .env 时 source 载入变量，修复 MYSQL_ROOT_PASSWORD unbound
- `386b9bb` refactor(job): 拆除 ypbin-job 空壳服务（任务调度移交 XXL-JOB）
- `0b0c027` sql(job): 退役 sys_job/sys_job_log 表与任务菜单种子（迁移 XXL-JOB）
- `f7c3747` refactor(system): 退役自研任务调度面 sys_job（迁移 XXL-JOB）
- `0a07ef8` deploy(xxl-job): 编排 XXL-JOB 调度中心（xxl-job-admin:3.4.2）
- `e06c95d` feat(system): 接入 XXL-JOB 执行器——公告定时发布扫描迁 @XxlJob
- `06a4e2e` style(ai): UserContext.getTenantId 回退写法 lambda 改方法引用
- `8958d61` feat(system): 补回个人头像上传接口 POST /user/profile/avatar
- `b990e53` docs(skill): 补充 starter 版本校验/Release 机制/Nacos 密码占位符踩坑
- `ee25fbf` fix(deploy): Nacos 配置密码占位符化——DB 密码不再硬编码提交
- `8034be2` fix(deploy): admin-ui 拉取分支 master 改 main（仓库默认分支为 main）
- `3c2c17a` fix(deploy): main 一键脚本补 -y/--yes 参数解析（对齐单体脚本）
- `dd3ddd3` fix(build): 依赖 starter 2.1.1 并修正 SysCacheTest 失败语义
- `6142004` docs(skill): ypbin-admin-dev 开发规范入库（供各会话/协作者统一使用）
- `cd1c55b` docs: docs 目录放开跟踪，提交微服务部署手册；删除已过时内部文档与已实施方案
- `5a6e3e0` chore(deploy): 微服务默认部署目录改为 /opt/ypbin/main
- `23bb931` ci(main): 微服务 CI 构建 starter 时跳过 jacoco
- `73fbbd1` docs(deploy): 修正微服务脚本默认部署目录注释
- `198e15a` chore: 微服务改为主分支后同步脚本分支引用为 main
- `be9522f` fix(deploy): 微服务脚本 starter 构建跳过 jacoco，保证新服务器一键部署可用
- `baeaf3a` fix(config): ai/job 关闭本地 Sa-Token 拦截，统一由网关身份头鉴权
- `7eaf68d` fix(config): system 关闭本地 Sa-Token 拦截，统一由网关身份头鉴权
- `a32a20f` refactor(microservice): AI 知识库/Prompt 模板列表查询返回 Resp 不裸返实体
- `3fdbe73` refactor(microservice): 移除冗余 @JsonSerialize(ToStringSerializer)，Long 转字符串由全局配置处理
- `dd182ec` refactor(microservice): 同步 Job/Notice/File/Message Resp 契约，不裸返实体
- `5865c82` fix(gateway): 引入 ypbin-starter-json 统一日期格式
- `410ae96` fix(gateway): 路由增加 /menu/** 支持前端菜单接口
- `9df20e1` fix(config): gateway Nacos 配置显式声明 sa-token，避免公共配置未生效
- `fd6208b` fix(gateway): 补充 Jackson2 JSR310 依赖解决 Sa-Token 启动缺类
- `011f405` fix(system): Feign 用户查询收敛到 service 并避免失败缓存
- `0730f2a` fix(gateway): 增加 sa-token reactor starter 支持 WebFlux Redis 会话
- `5acb9ea` fix(config): sa-token 配置下沉公共 Nacos，auth/gateway 共享会话
- `f4fa276` fix(deps): sa-token-redis-jackson 补充显式版本 1.46.0
- `1a4f5a1` fix: 跨服务 sa-token 会话共享与 Feign/脱敏健壮性
- `47a521c` feat(deploy): 一键部署增加前端 admin-ui
- `411279b` fix(auth): 未传 tenantId 时按全局定位，不强制匹配租户
- `a293ae3` fix(deploy): 初始化 MySQL 前等待容器健康
- `aa71149` fix(deploy): install.sh 适配 Nacos 3 Console API 并初始化 MySQL
- `89dbb57` chore(deploy): 清理单体部署，统一微服务 deploy/install.sh 与 SQL 初始化
- `d7165f9` refactor(auth): 租户提供者收敛到 ypbin-common 自动装配
- `1836690` fix(auth): 微服务登录支持可选租户并补齐 TenantProvider
- `4cc0b22` fix(deploy): 升级 Nacos 3.2.4 并修正微服务配置
- `a9377a3` refactor(config): Nacos 配置写死连接参数，去掉 compose env 依赖
- `b70af96` fix: 微服务主类补 @MapperScan（Mapper 未注册致 NoSuchBeanDefinition）
- `0679747` fix(deps): 补 context-propagation（tenant optional 不传递）与 auth mysql 驱动
- `ae8e78a` refactor(config): 配置全量下沉 Nacos——本地 yml 仅端口+应用名（对齐 blade）
- `2d4dc9e` fix(deploy): 微服务补数据源/Redis 配置（compose 容器互联）
- `0681d78` fix(gateway): 显式禁用 Spring Cloud 兼容性检查（EPP 注入未生效，yml 兜底）
- `4f1e7d8` fix(gateway): 排除 webmvc 传递依赖（WebFlux 网关 ClassNotFound WebMvcConfigurer）
- `ba423ff` fix(deploy): SERVICES 目录路径修正——system/ai/job 实际在 ypbin-service/ 子目录
- `cf733c3` fix(build): 5 个服务 spring-boot-maven-plugin 补 repackage execution（打 fat jar）
- `020f9c6` fix(deploy): 打包 find 路径重复前缀 bug（ypbin-$dir → $dir）
- `bd20239` fix(deploy): 微服务一键脚本——独立目录 + 交互菜单 + 分叉强对齐
- `781988f` fix(deploy): 一键脚本补非 root 自提权（对齐单体脚本）
- `e103556` fix(deploy): 一键脚本健壮性——拉取强校验/构建错误完整输出/Maven 阿里云镜像
- `de54d30` feat(deploy): 微服务一键部署脚本 + 5 服务 Dockerfile + compose 内嵌基础设施
- `38562cb` chore(deps): ypbin-starter 依赖升级 2.0.0 → 2.1.0（微服务增强版本）
- `1154dc7` fix(system): 复核细节修复——菜单缓存 N+1/角色变更漏清权限缓存
- `50d2946` fix(system): 审查缺陷修复——角色/菜单权限缓存失效 + 旧键残留 + FQCN 清理
- `0bc3911` docs: 缓存方案更新 v3.0（@CacheEvict 声明式主动失效）
- `855e55f` refactor(cache): 手动清缓存改 @CacheEvict 声明式注解（防遗漏）
- `afa6bcc` feat(cache): 微服务跨服务查询全量永久缓存 + 主动失效（对齐参考架构思路）
- `e275533` chore: 清理注释/文档/pom 中的参考项目品牌词
- `92d5a6f` feat(microservice): 补齐全部缺失业务接口 + 修复身份上下文/跨服务直连缺陷
- `868225f` refactor: @PlatformAccess 改用 starter 反哺版本，删除 admin 重复实现
- `125f336` refactor(microservice): Feign 规范对齐参考架构——api/service 双 feign 目录 + 反哺 starter 身份头
- `e92ebb1` refactor(auth,ai): 跨服务查询改走 Feign + 缓存
- `1b51171` fix(microservice): 主动自审修复 6 项缺陷——服务命名/依赖冗余/网关路由
- `0430098` refactor(microservice): 实体/枚举/DTO 去重——每类只保留 system-api 唯一正本
- `5efe663` refactor(microservice): 全面结构修复——目录与包对齐、mapper 归位、空壳清理
- `2b4f9d0` refactor(microservice): 实体/DTO/VO 迁移至 api 模块（api 模块承载共享契约）
- `ba53d3d` refactor(microservice): 单体代码删除 + 目录扁平化——微服务独立为根模块
- `86c1fb1` refactor(microservice): 目录精简——去 -biz 后缀 + AdminConstants 上移 ypbin-common
- `010491e` fix(microservice): 重构审查修复——auth artifactId 与 docker-compose 路径对齐新结构
- `b1807e8` refactor(microservice): 目录结构对齐参考架构——service/api 分离 + common 公共模块
- `d167357` refactor(microservice): 代码审查修复——魔法值/死代码/异常处理/共享类标注
- `67acd23` docs(microservice): 方案进度更新至 v1.4（M4 Feign 跨服务打通）
- `40c2c60` feat(microservice): M4——Feign 跨服务调用打通（auth → system 权限/角色）
- `09586b1` docs(microservice): 方案进度更新至 v1.3（M3 全部服务迁入）
- `f62e033` feat(microservice): M3 后半——ai-svc 全量迁入 + job-svc 调度框架
- `b3c7476` docs(microservice): 方案文档入库（v1.2，M3 system 域已迁入）——docs/ 被 .gitignore 忽略，强制跟踪
- `4b5d9fc` feat(microservice): M3——system-svc 迁入核心管理域（用户/角色/菜单/部门/岗位/配置）
- `aeb2bc3` feat(microservice): M2 收尾——auth-svc 登录链路迁入
- `718860a` feat(microservice): M2 认证链路——网关鉴权 + 下游身份头上下文
- `2fcc6d0` feat(microservice): 微服务化改造 M1 骨架——方案文档 + 网关 + 四服务空壳

## 4. boot / boot-miniapp —— 已回灌记录与剩余差距

### 4.1 boot 分支独有的提交（48 个 first-parent，即「回灌/单体线改造」记录）

> 这些提交是 boot 线对 main 功能的手工移植与单体化改造；**没有对应 PR 号**，所以只能按主题与 main 清单人工配对。
> 已配对的高置信项：访问黑名单管理端（`8d4b51b` `9687f97` ↔ main `ab54095` `49826ca`）、强制首登改密（`acb6534`）、
> 公告详情/在线用户分页（`cfa433c` ↔ main `#110` 系列）、AI 对话入参（`d737237`）、P2 整改（`baa0507` `a713e31` `f70e54e` `96f1ef9`）。
> **其余 main 清单项在 boot 上没有对应提交**，即为待回灌项（尤其 §2 的 P0/P1 项与 2026-10-20 之后的部署/空间/黑名单修复）。


#### 2026-11

- `7d165d0` Merge pull request #127 from wenbin-wb/claude/boot-starter-4.4.0
- `9687f97` feat(security): 黑名单临时封禁接口与权限（boot 线）
- `8d4b51b` feat(security): 访问黑名单管理端同步 boot 线
- `cfa433c` fix: 公告详情接口 + 在线用户分页（对齐前端契约）
- `acb6534` feat(security): 平台超管强制首登改密（单体线）
- `d737237` fix(ai): 对话入参不再静默失效（模型、图片、流式开关）
- `96f1ef9` refactor: P2 收尾清理（敏感字段、密钥脱敏、SSE 释放、死代码）
- `f70e54e` perf: P2 第三批 A 组（模板 N+1、公告分页、导出上限、字典缓存、AI 统计原子累加）
- `a713e31` fix: P2 审查整改第二批（租户禁用踢会话、授权审批 CAS、文件删除事务顺序）
- `baa0507` fix: P2 审查整改第一批（公告 NULL CAS、用户状态保护）
- `cf5ccbf` fix: 代码审查 P0/P1 整改（单体线）

#### 2026-10

- `68c3608` chore(deps): boot 分支 ypbin-starter 升级 4.1.0 -> 4.2.1（对齐最新 Release）
- `2392c65` refactor(db): 整合 Flyway 脚本（系统 + AI 合并为 V1/V2）
- `7c9b316` chore(deps): boot 分支 ypbin-starter 升级 3.7.0 -> 4.1.0（履行 README 发版跟进义务）
- `3a501b5` chore(deps): ypbin-starter 升级至 v3.7.0 (#79)

#### 2026-09

- `19c28cb` chore(deps): ypbin-starter 升级至 v3.5.0
- `d213b26` docs(boot): 写明本分支的维护档位、升级义务与退役判据
- `aa7a1f0` chore(boot): 升级 starter 3.1.0 → 3.4.0，并对齐两个 workflow (#65)
- `ede4516` chore(ci): 三个分支统一触发 CI/CodeQL，并对齐 Action 版本
- `9189272` chore(deps): boot 分支升级 ypbin-starter 2.2.1 → 3.1.0（跨两个大版本的适配）
- `b6c34fa` chore(deps): boot 升级 ypbin-starter 2.2.0 → 2.2.1（SSE 微服务身份头修复）
- `a3f6dbb` refactor(api): 单体路径对齐服务唯一段（与 main 同源前端契约）
- `10f2b34` chore(deps): boot 升级 ypbin-starter 2.1.1 → 2.2.0（新增 xxljob 接入壳）
- `6d14ef9` sql(xxl-job): boot 退役 sys_job 表/菜单种子，接入 XXL-JOB 配置
- `ecdc89f` refactor(job): boot 单体迁移 XXL-JOB——执行器 @XxlJob 化 + 退役自研任务面
- `30d1d6a` docs(skill): 同步 main 的 starter 版本校验/Nacos 密码占位符踩坑补充
- `92ae9da` chore(deps): boot 单体升级 ypbin-starter 2.0.0 → 2.1.1
- `23a17dd` docs(skill): ypbin-admin-dev 开发规范入库（与 main 同步，供各会话/协作者统一使用）
- `663c238` chore: docs 目录放开跟踪（与 main 对齐），保留 .claude 忽略
- `1b23835` chore(deploy): 单体默认部署目录改为 /opt/ypbin/boot
- `4875452` ci(boot): 单体分支 CI 改为监听 boot，并跳过 starter jacoco
- `94bc5df` chore: 单体分支改为 boot 后同步部署脚本与文档引用
- `63c3141` fix(deploy): 单体部署固定使用 starter v2.0.0，避免 main 误装 master(2.1.0)
- `3b09c29` fix(deploy): starter 构建跳过 jacoco 覆盖率检查，保证新服务器一键部署可用
- `6ebadc9` refactor(monolith): AI 知识库/Prompt 模板列表查询返回 Resp 不裸返实体
- `1878bd8` refactor: 移除冗余 @JsonSerialize(ToStringSerializer)，Long 转字符串由全局配置处理
- `33d6906` refactor(monolith): 站内信列表/最近消息返回 MessageResp 不裸返实体
- `b48400d` refactor(monolith): 文件列表查询返回 FileResp 不裸返实体
- `04064ad` refactor(monolith): 公告列表查询返回 NoticeResp 不裸返实体
- `6e0185d` refactor(monolith): job 列表查询返回 JobResp 不裸返实体
- `244f85c` chore(monolith): 更新 ypbin-admin-system 模块描述为 modules 分层
- `7830aaa` docs: 单体项目结构更新为 modules 分层说明
- `cbb295b` refactor(monolith): job 业务域独立为 modules.job
- `c8faa71` fix(monolith): AI 迁入 modules 后同步 package 为 modules.ai
- `8cb0adf` refactor(monolith): auth 业务域独立为 modules.auth
- `3540604` refactor(monolith): AI/system 迁入 modules 目录统一包结构
- `ec068d2` refactor(monolith): 合并 ypbin-admin-common 到 system 模块的 common 包
- `c53b6cb` refactor(monolith): 按业务域提升 AI 包为 cn.ypbin.admin.ai

### 4.2 boot-miniapp 分支独有的提交（51 个 first-parent）

> 先与 boot 对齐（两者差 13 个提交），再按 §3 清单回灌；小程序专属改动见下列提交。


#### 2026-11

- `c559903` Merge pull request #131 from wenbin-wb/claude/boot-miniapp-starter-4.4.0
- `f82b30a` feat(security): 黑名单临时封禁接口与权限
- `ec2ab92` docs: 补充访问黑名单能力说明
- `03d12e9` feat(security): 访问黑名单管理端（单体 boot 线）
- `1dcd1aa` merge: 同步 boot 的公告详情与在线用户分页
- `b2c2dcb` merge: 同步 boot 的平台超管强制改密
- `c3d0f56` merge: 同步 boot 的 AI 对话入参修复
- `c36604f` merge: 同步 boot 的 P2 收尾清理
- `62956a4` fix(miniapp): 公告服务接口保留 List 导入（小程序可见公告列表）
- `f0a1dc6` merge: 同步 boot 的 P2 第三批 A 组
- `879ca45` merge: 同步 boot 的 P2 第二批整改
- `d8ccd8a` merge: 同步 boot 的 P2 第一批整改
- `55d59b8` merge: 同步 boot 的代码审查 P0/P1 整改

#### 2026-10

- `ed21e88` refactor(db): 整合 Flyway 脚本（基础 V1/V2 + 小程序单独 V3）
- `69db4f6` fix(miniapp): 修复审查发现的资金并发、登录状态与放行路径问题
- `fece5a1` feat(miniapp): 小程序后端切换到单体分支（boot-miniapp）
- `7c9b316` chore(deps): boot 分支 ypbin-starter 升级 3.7.0 -> 4.1.0（履行 README 发版跟进义务）
- `3a501b5` chore(deps): ypbin-starter 升级至 v3.7.0 (#79)

#### 2026-09

- `19c28cb` chore(deps): ypbin-starter 升级至 v3.5.0
- `d213b26` docs(boot): 写明本分支的维护档位、升级义务与退役判据
- `aa7a1f0` chore(boot): 升级 starter 3.1.0 → 3.4.0，并对齐两个 workflow (#65)
- `ede4516` chore(ci): 三个分支统一触发 CI/CodeQL，并对齐 Action 版本
- `9189272` chore(deps): boot 分支升级 ypbin-starter 2.2.1 → 3.1.0（跨两个大版本的适配）
- `b6c34fa` chore(deps): boot 升级 ypbin-starter 2.2.0 → 2.2.1（SSE 微服务身份头修复）
- `a3f6dbb` refactor(api): 单体路径对齐服务唯一段（与 main 同源前端契约）
- `10f2b34` chore(deps): boot 升级 ypbin-starter 2.1.1 → 2.2.0（新增 xxljob 接入壳）
- `6d14ef9` sql(xxl-job): boot 退役 sys_job 表/菜单种子，接入 XXL-JOB 配置
- `ecdc89f` refactor(job): boot 单体迁移 XXL-JOB——执行器 @XxlJob 化 + 退役自研任务面
- `30d1d6a` docs(skill): 同步 main 的 starter 版本校验/Nacos 密码占位符踩坑补充
- `92ae9da` chore(deps): boot 单体升级 ypbin-starter 2.0.0 → 2.1.1
- `23a17dd` docs(skill): ypbin-admin-dev 开发规范入库（与 main 同步，供各会话/协作者统一使用）
- `663c238` chore: docs 目录放开跟踪（与 main 对齐），保留 .claude 忽略
- `1b23835` chore(deploy): 单体默认部署目录改为 /opt/ypbin/boot
- `4875452` ci(boot): 单体分支 CI 改为监听 boot，并跳过 starter jacoco
- `94bc5df` chore: 单体分支改为 boot 后同步部署脚本与文档引用
- `63c3141` fix(deploy): 单体部署固定使用 starter v2.0.0，避免 main 误装 master(2.1.0)
- `3b09c29` fix(deploy): starter 构建跳过 jacoco 覆盖率检查，保证新服务器一键部署可用
- `6ebadc9` refactor(monolith): AI 知识库/Prompt 模板列表查询返回 Resp 不裸返实体
- `1878bd8` refactor: 移除冗余 @JsonSerialize(ToStringSerializer)，Long 转字符串由全局配置处理
- `33d6906` refactor(monolith): 站内信列表/最近消息返回 MessageResp 不裸返实体
- `b48400d` refactor(monolith): 文件列表查询返回 FileResp 不裸返实体
- `04064ad` refactor(monolith): 公告列表查询返回 NoticeResp 不裸返实体
- `6e0185d` refactor(monolith): job 列表查询返回 JobResp 不裸返实体
- `244f85c` chore(monolith): 更新 ypbin-admin-system 模块描述为 modules 分层
- `7830aaa` docs: 单体项目结构更新为 modules 分层说明
- `cbb295b` refactor(monolith): job 业务域独立为 modules.job
- `c8faa71` fix(monolith): AI 迁入 modules 后同步 package 为 modules.ai
- `8cb0adf` refactor(monolith): auth 业务域独立为 modules.auth
- `3540604` refactor(monolith): AI/system 迁入 modules 目录统一包结构
- `ec068d2` refactor(monolith): 合并 ypbin-admin-common 到 system 模块的 common 包
- `c53b6cb` refactor(monolith): 按业务域提升 AI 包为 cn.ypbin.admin.ai

## 5. feature/miniapp-backend —— 可直接合并 main

- 差距：main 领先 96 个提交（67 个 first-parent）；分支独有 44 个。
- dry-run 合并（`git merge-tree --write-tree --messages origin/feature/miniapp-backend origin/main`）冲突 **27 个文件**：
- `Merge conflict in CHANGELOG.md`
- `Merge conflict in deploy/.env.example`
- `Merge conflict in deploy/docker-compose.yml`
- `Merge conflict in deploy/install.sh`
- `Merge conflict in deploy/nacos/ypbin-ai.yaml`
- `Merge conflict in deploy/nacos/ypbin-auth.yaml`
- `Merge conflict in docs/microservice-deployment.md`
- `Merge conflict in pom.xml`
- `Merge conflict in ypbin-auth/src/main/java/cn/ypbin/admin/auth/AuthServiceApplication.java`
- `Merge conflict in ypbin-auth/src/main/java/cn/ypbin/admin/auth/controller/AuthController.java`
- `Merge conflict in ypbin-auth/src/main/java/cn/ypbin/admin/auth/service/AuthService.java`
- `Merge conflict in ypbin-auth/src/main/java/cn/ypbin/admin/auth/service/LoginSupport.java`
- `Merge conflict in ypbin-auth/src/main/java/cn/ypbin/admin/auth/service/SmsCodeService.java`
- `ypbin-service-api/ypbin-ai-api/src/main/java/cn/ypbin/admin/ai/model/resp/AiMessageResp.java renamed to ypbin-service/ypbin-miniapp/src/main/java/cn/ypbin/admin/miniapp/common/model/req/UserProfileUpdateReq.java in origin/feature/miniapp-backend and to ypbin-service-api/ypbin-system-api/src/main/java/cn/ypbin/admin/system/model/resp/AccessBlocklistResp.java in origin/main.`
- `Merge conflict in ypbin-service-api/ypbin-system-api/src/main/java/cn/ypbin/admin/system/api/feign/ISystemClient.java`
- `Merge conflict in ypbin-service-api/ypbin-system-api/src/main/java/cn/ypbin/admin/system/api/feign/ISystemClientFallback.java`
- `Merge conflict in ypbin-service-api/ypbin-system-api/src/main/java/cn/ypbin/admin/system/model/resp/LoginResp.java`
- `Merge conflict in ypbin-service/ypbin-ai/src/main/java/cn/ypbin/admin/ai/service/impl/AiChatServiceImpl.java`
- `Merge conflict in ypbin-service/ypbin-ai/src/main/java/cn/ypbin/admin/ai/service/impl/AiModelConfigServiceImpl.java`
- `Merge conflict in ypbin-service/ypbin-ai/src/main/java/cn/ypbin/admin/ai/service/impl/AiShareServiceImpl.java`
- `Merge conflict in ypbin-service/ypbin-ai/src/main/java/cn/ypbin/admin/ai/service/impl/AiWidgetServiceImpl.java`
- `Merge conflict in ypbin-service/ypbin-system/src/main/java/cn/ypbin/admin/system/feign/SystemClientImpl.java`
- `Merge conflict in ypbin-service/ypbin-system/src/main/java/cn/ypbin/admin/system/service/impl/SysTenantServiceImpl.java`
- `Merge conflict in ypbin-service/ypbin-system/src/main/java/cn/ypbin/admin/system/service/impl/UserProfileServiceImpl.java`
- `Merge conflict in ypbin-service/ypbin-system/src/test/java/cn/ypbin/admin/system/feign/SystemClientImplLogIngestTest.java`
- `Merge conflict in ypbin-service/ypbin-system/src/test/java/cn/ypbin/admin/system/feign/SystemClientImplPlatformUserTest.java`
- `Merge conflict in ypbin-service/ypbin-system/src/test/java/cn/ypbin/admin/system/feign/SystemClientImplUserByIdTest.java`

## 6. iot 仓（`ypbin-iot`）

- 纪律：`SYNC.md` 规定只跟踪 `upstream/main`，`git fetch --no-tags upstream && git merge upstream/main`，冲突必须 ⊆ 白名单。
- 当前：
  - PR **#318**（`sync/admin-main-20261107`）已把 upstream 的 11 个提交合入（starter 4.4.0、访问黑名单管理端、auth 黑名单计数/自封修复等）；
  - 其后仍缺 upstream 的 **3 组提交**：
    - `043015c0` / `52d5602e`（#130 客户端 IP 口径统一 + `trusted-proxies`）；
    - `690154f8`（starter 4.4.1 同步）与 `b2940b5d` / `73da93f1`（回退到 4.4.0）——由本仓「固定 4.4.0 + 租户兼容层」策略吸收，不需要跟。
  - PR **#319**（回退 4.4.0 + `TenantInsertCompatibilityConfiguration` + IT 复用同款处理器）用于解除 4.4.0 的租户 INSERT 阻塞。
- 行动顺序：#319 合并 → #318 合并（可能需再次 rebase/merge 解决 pom 冲突）→ 再 `merge upstream/main` 取 #130 → `mvn clean verify`。

## 7. 建议节奏

1. **iot 先行**（线上阻塞最直接）：合 #319 → 合 #318 → 补 #130。
2. **feature/miniapp-backend**：按 §5 冲突清单合并 main，解决后全量 verify；之后每次 starter 发版/安全修复后同步一次，避免再次积压到 90+ 提交。
3. **boot / boot-miniapp**：以 §3 清单为工作项，按 P0→P1→P2 逐项回灌；两条单体线先互相对齐再回灌，避免同一功能在两条线上又分叉。
4. **starter 4.4.1**：配额恢复（12-01）后按 `RELEASING.md` 的「先 SNAPSHOT 回归、再发正式版」发布；届时删除 iot/admin 的租户兼容层并升级。

## 8. 复现与核对命令

```bash
# main 领先各分支的提交（first-parent，按功能单位）
git log --first-parent --oneline origin/boot..origin/main
git log --first-parent --oneline origin/feature/miniapp-backend..origin/main

# boot / boot-miniapp 的回灌记录
git log --first-parent --oneline origin/main..origin/boot
git log --first-parent --oneline origin/main..origin/boot-miniapp

# feature 分支的合并冲突预演（不落盘）
git merge-tree --write-tree --messages origin/feature/miniapp-backend origin/main

# iot：upstream 差距
cd ../ypbin-iot && git fetch --no-tags upstream && git log --oneline HEAD..upstream/main
```
