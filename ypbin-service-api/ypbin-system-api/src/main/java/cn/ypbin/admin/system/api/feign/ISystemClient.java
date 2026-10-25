/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.api.feign;

import cn.ypbin.admin.system.model.dto.ConfigValue;
import cn.ypbin.admin.system.model.dto.SocialAuthConfig;
import cn.ypbin.admin.system.model.dto.SysSpaceDto;
import cn.ypbin.admin.system.model.dto.SysSpaceRelationDto;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import cn.ypbin.admin.system.model.req.SpaceSyncReq;
import cn.ypbin.admin.system.model.resp.RouteResp;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.tracking.core.TrackEvent;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 系统管理服务 Feign 接口（供 auth/ai 等调用）。
 *
 * <p>内部调用由网关签发身份头，Feign 拦截器（starter-cloud-core）自动透传，
 * 服务端从 {@code X-User-Id} 等头识别调用者身份。查询结果建议经
 * {@code SysCache} 缓存，避免高频 RPC。调用失败走 {@link ISystemClientFallback}
 * 降级返回失败 {@code R}。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@FeignClient(name = "ypbin-system", path = "/internal",
    fallback = ISystemClientFallback.class)
public interface ISystemClient {

    /**
     * 查询用户权限码。
     */
    @GetMapping("/permissions")
    R<List<String>> listPermissions(@RequestParam("userId") Long userId);

    /**
     * 查询用户角色码。
     */
    @GetMapping("/role-codes")
    R<List<String>> listRoleCodes(@RequestParam("userId") Long userId);

    /**
     * 判定用户是否为平台用户（{@code PLATFORM} 类型、启用且未逻辑删除）。
     *
     * <p>供各服务实现 starter 的 {@code PlatformUserChecker} 端口用：{@code @PlatformAccess}
     * 标注的资源在宿主未提供判定器时一律拒绝（fail-closed），而调用方服务禁止直连共享库，
     * 故判定口径只有 system 一份实现（{@code SysPermissionServiceImpl}），调用方经本端点复用，
     * 避免同一规则在多服务里各写一遍而产生漂移。</p>
     *
     * <p>失败语义：system 不可达时走 {@link ISystemClientFallback} 返回失败 {@code R}，
     * 调用方必须据 {@code R.success}/{@code R.code} 判定并上抛异常（禁止静默当作「非平台用户」）。</p>
     */
    @GetMapping("/platform-user")
    R<Boolean> isPlatformUser(@RequestParam("userId") Long userId);

    /**
     * 查询用户可访问的路由树（登录后动态菜单）。
     */
    @GetMapping("/routes")
    R<List<RouteResp>> listRoutes(@RequestParam("userId") Long userId);

    /**
     * 按用户名查询用户（登录用）。
     */
    @GetMapping("/user-by-username")
    R<SysUserDto> getUserByUsername(@RequestParam("username") String username);

    /**
     * 按 ID 查询用户。
     */
    @GetMapping("/user-by-id")
    R<SysUserDto> getUserById(@RequestParam("userId") Long userId);

    /**
     * 按手机号查询用户（手机验证码登录用）。
     */
    @GetMapping("/user-by-phone")
    R<SysUserDto> getUserByPhone(@RequestParam("phone") String phone);

    /**
     * 记录最后登录时间（登录成功收尾用）。
     */
    @GetMapping("/update-last-login")
    R<Void> updateLastLoginTime(@RequestParam("userId") Long userId);

    /**
     * 按关键词搜索用户（AI 工具用，限制 10 条）。
     */
    @GetMapping("/search-users")
    R<List<SysUserDto>> searchUsers(@RequestParam("keyword") String keyword);

    /**
     * 用户计数（AI 工具统计用）。
     */
    @GetMapping("/user-count")
    R<Long> countUsers();

    /**
     * 按参数键读取系统参数（auth 读取登录开关/短信配置用）。
     *
     * <p><strong>脱敏契约（跨服务可见行为）</strong>：键名命中敏感后缀（{@code _SECRET} /
     * {@code _PASSWORD} / {@code _TOKEN} / {@code _ACCESS_KEY} / {@code _PRIVATE_KEY} /
     * {@code _API_KEY}，忽略大小写）时，返回值<strong>不是原文</strong>，而是
     * {@code ****} + 末 4 位（长度 ≤ 4 时整体为 {@code ****}）；与 system 管理面列表页同一判据、
     * 同一形态。需要 Key 原文的调用方（如短信/邮件发送）必须走系统服务内部通道，不得经本接口获取。
     * 判据与形态的完整说明与选择理由见 system 模块的 {@code ConfigValueMasker}。</p>
     *
     * @param configKey 参数键
     * @return 参数取值（敏感键为脱敏形态）
     */
    @GetMapping("/config-by-key")
    R<ConfigValue> getConfigByKey(@RequestParam("configKey") String configKey);

    /**
     * 密码校验（system 直查库比对；密码不落缓存，改密即时生效）。
     */
    @PostMapping("/verify-password")
    R<Boolean> verifyPassword(@RequestParam("userId") Long userId,
        @RequestParam("rawPassword") String rawPassword);

    /**
     * 第三方登录平台授权配置（auth 构建授权请求用，含密钥明文，仅限内部传递）。
     */
    @GetMapping("/social-auth-config")
    R<SocialAuthConfig> getSocialAuthConfig(@RequestParam("source") String source);

    /**
     * 全部第三方登录平台授权配置（auth 拉取启用平台列表用）。
     */
    @GetMapping("/social-auth-configs")
    R<List<SocialAuthConfig>> listSocialAuthConfigs();

    /**
     * 按平台与 openId 查绑定（第三方登录用）。
     */
    @GetMapping("/social-binding")
    R<SysUserSocialDto> getSocialBinding(@RequestParam("platform") String platform,
        @RequestParam("openId") String openId);

    /**
     * 用户是否已绑定指定平台。
     */
    @GetMapping("/social-user-bound")
    R<Boolean> isSocialUserBound(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform);

    /**
     * 按平台与 openId 是否已绑定其他用户。
     */
    @GetMapping("/social-account-bound")
    R<Boolean> isSocialAccountBound(@RequestParam("platform") String platform,
        @RequestParam("openId") String openId);

    /**
     * 新增第三方绑定。
     */
    @PostMapping("/social-bind-save")
    R<Void> saveSocialBinding(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform, @RequestParam("openId") String openId,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar,
        @RequestParam(value = "accessToken", required = false) String accessToken);

    /**
     * 解绑（按用户+平台）。
     */
    @PostMapping("/social-unbind")
    R<Void> unbindSocial(@RequestParam("userId") Long userId,
        @RequestParam("platform") String platform);

    /**
     * 用户已绑定的平台列表。
     */
    @GetMapping("/social-bindings")
    R<List<SysUserSocialDto>> listSocialBindings(@RequestParam("userId") Long userId);

    /**
     * 上报一条操作/登录日志（auth、ai 等无数据源的调用方专用）。
     *
     * <p>上报的是采集完成的 {@link LogRecord} 本身，system 侧直接交给既有的
     * {@code LogDao}（{@code DbLogProviders.DbLogDao}）落 {@code sys_log}；
     * 调用方不得再自建一套字段映射，否则同一张表会有两份口径。</p>
     *
     * <p>失败语义：system 不可达时走 {@link ISystemClientFallback} 返回失败 {@code R}，
     * 调用方必须据 {@code R.success}/{@code R.code} 判定并上抛异常（禁止静默丢弃），
     * 由调用侧记完整堆栈。</p>
     */
    @PostMapping("/log-ingest")
    R<Void> ingestLog(@RequestBody LogRecord logRecord);

    /**
     * 上报一批后端业务埋点事件（auth 等无埋点落库能力的调用方专用）。
     *
     * <p>承载的是 starter 采集模型 {@link TrackEvent} 本身，system 侧直接交给其容器内的
     * {@code TrackRecorder}（唯一写入口，未登记事件码在此被拒绝），因此「事件码登记校验 →
     * 有界队列 → 消费者线程 → {@code sys_track_event} 落库」全仓只有一份实现；
     * 调用方只是搬运，不做任何字段改名映射。</p>
     *
     * <p>失败语义：system 不可达或埋点未启用时返回失败 {@code R}，调用方必须据
     * {@code R.success}/{@code R.code} 判定并记完整堆栈（禁止静默丢弃）。</p>
     *
     * @param events 待落库事件；调用方须已在请求线程上捕获 IP/UA/链路 ID/用户等上下文
     * @return 统一响应体
     */
    @PostMapping("/track-ingest")
    R<Void> ingestTrackEvents(@RequestBody List<TrackEvent> events);

    /**
     * 按用户名查询用户，不存在则创建（各端侧登录通用，不绑定具体端侧）。
     *
     * <p>端侧标识由调用方通过 {@code userType} 传入（如小程序传 {@code MINIAPP}），
     * 基础契约不写死任何端侧语义。返回只读视图 {@link SysUserDto}
     * （与其余用户查询一致，不暴露持久化实体）。</p>
     *
     * <p><b>{@code userType} 与 {@code defaultRealName} 都是必填</b>（仅在<b>新建用户</b>时使用）：
     * 端侧标识与「空昵称时的展示名」必须由调用方显式给出——不接受静默默认值
     * （静默默认会让「调用方漏传」变成看不见的落库语义变化）。</p>
     *
     * <p><b>已存在用户不会被 {@code defaultRealName} 改名</b>（只有显式传了非空昵称才更新）；
     * 这两个参数此时<b>不参与写入，也不做业务校验</b>（HTTP 层仍要求参数存在）。</p>
     */
    @PostMapping("/user-get-or-create")
    R<SysUserDto> getOrCreateUserByUsername(@RequestParam("username") String username,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar,
        @RequestParam("userType") String userType,
        @RequestParam("defaultRealName") String defaultRealName);

    /**
     * 更新用户资料（昵称、头像、手机号）。
     */
    @PostMapping("/user-update-profile")
    R<SysUserDto> updateUserProfile(@RequestParam("userId") Long userId,
        @RequestParam(value = "nickname", required = false) String nickname,
        @RequestParam(value = "avatar", required = false) String avatar,
        @RequestParam(value = "phone", required = false) String phone);

    // ---------- 空间（基础主数据的跨服务只读/写入通道） ----------

    /**
     * 按 ID 查询单个空间（含完整路径与类型中文名）。
     *
     * <p><b>为什么业务服务必须走这里而不是直连 {@code sys_space}</b>：空间是基础模块的主数据，
     * 跨服务禁止访问对方表（本仓既有约定，见 {@code docs/microservice-deployment.md}）。
     * IoT 设备详情要显示「设备在 A栋 1F 101室」时，调本端点即可，不需要理解空间表结构。</p>
     *
     * <p>失败语义：空间不存在时返回<b>失败</b> {@code R}（不是成功 + null）——
     * 调用方据此区分「查询失败」与「确实没有空间」，不得静默降级成「无位置」。</p>
     *
     * @param spaceId 空间 ID
     * @return 空间摘要
     */
    @GetMapping("/space-by-id")
    R<SysSpaceDto> getSpaceById(@RequestParam("spaceId") Long spaceId);

    /**
     * 查询全部空间（树形拍平，含完整路径），供业务服务做本地下拉/筛选。
     *
     * <p><b>返回全量而非分页</b>：空间是典型的小集合（一个租户几十到几百个），
     * 业务侧要按空间筛选设备时需要完整列表做下拉；分页会让调用方必须自己循环拉取。</p>
     *
     * <p>调用方应<b>缓存</b>结果（空间变更频率低），不要每次页面请求都调。</p>
     *
     * @return 空间摘要列表（按父子顺序）
     */
    @GetMapping("/space-list")
    R<List<SysSpaceDto>> listSpaces();

    /**
     * 按业务对象反查其所在空间。
     *
     * @param targetType 关联对象类型（dept/user/device/asset…）
     * @param targetId   关联对象 ID
     * @return 关联列表（含空间完整路径）
     */
    /**
     * **批量**反查：一次取多个业务对象各自的空间关联（列表页展示「所在空间」用）。
     *
     * <p><b>为什么必须批量</b>：列表一页几十条设备，逐条调单对象反查就是 N 次跨服务 HTTP。
     * 本端点一次返回，调用方按 targetId 取值。</p>
     *
     * @param targetType 关联对象类型
     * @param targetIds  关联对象 ID 列表
     * @return 对象 ID → 空间关联列表（无关联的对象不出现在结果里）
     */
    /**
     * 整体设置某业务对象的空间集合（**单事务语义**：算差集后批量增删）。
     *
     * <p><b>为什么需要它而不是让调用方 bind+unbind</b>：设备换位置时若由调用方自己做
     * 「先解绑旧的、再绑新的」，两步之间失败就留下不一致状态（两头都不在，或都在）。
     * 本端点在一个事务里完成整体替换。</p>
     *
     * @param req 同步请求（targetType + targetId + 目标 spaceIds）
     * @return 实际变更的关联条数
     */
    @PostMapping("/space-relations-sync")
    R<Integer> syncSpaceRelations(@RequestBody SpaceSyncReq req);

    @PostMapping("/space-relations-batch")
    R<Map<Long, List<SysSpaceRelationDto>>> listSpaceRelationsByTargets(
        @RequestParam("targetType") String targetType,
        @RequestBody List<Long> targetIds);

    @GetMapping("/space-relations-by-target")
    R<List<SysSpaceRelationDto>> listSpaceRelationsByTarget(
        @RequestParam("targetType") String targetType,
        @RequestParam("targetId") Long targetId);

    /**
     * 取指定空间及其全部后代 ID（供业务侧「按空间筛选」实现含子树语义）。
     *
     * <p>业务侧（如 IoT）需要「选 A栋 时看到其下所有设备」，而设备挂在最末级房间；
     * 业务服务没有空间表，也不该自己递归查 system 的表——故由 system 一次算好。</p>
     *
     * @param spaceId 根空间 ID
     * @return 该空间及全部后代 ID（含自身；不存在时为空列表）
     */
    @GetMapping("/space-subtree-ids")
    R<List<Long>> listSpaceSubtreeIds(@RequestParam("spaceId") Long spaceId);

    /**
     * 按空间（**含全部后代**）取挂在其上的业务对象 ID。
     *
     * <p>供业务侧「空间视角看设备」用：设备挂在末级房间，用户在空间页点「A栋」
     * 要看到其下所有设备，故必须含子树。</p>
     *
     * @param spaceId    根空间 ID
     * @param targetType 关联对象类型（可空：不限定类型）
     * @return 业务对象 ID 列表（去重）
     */
    @GetMapping("/space-target-ids-by-space")
    R<List<Long>> listSpaceTargetIdsBySpace(@RequestParam("spaceId") Long spaceId,
        @RequestParam(value = "targetType", required = false) String targetType);

    /**
     * 按空间（含后代）统计各类型业务对象数量（供空间列表「设备数」列）。
     *
     * @param spaceIds 根空间 ID 列表
     * @return 空间 ID → (对象类型 → 数量)
     */
    @PostMapping("/space-relation-counts")
    R<Map<Long, Map<String, Long>>> countSpaceRelationsBySpaceTrees(
        @RequestBody List<Long> spaceIds);

    /**
     * 把一批业务对象绑定到指定空间（幂等：已存在的关联会被更新而不是重复插入）。
     *
     * <p><b>写入为什么也走本端点</b>：{@code sys_space_relation} 属基础模块的表，
     * 业务服务直写它等于绕过租户插件与唯一键校验。走本端点由 system 统一做
     * 「空间存在性 + 租户隔离 + 主空间唯一 + 类型合法性」四项校验。</p>
     *
     * @param spaceId     空间 ID
     * @param targetType  关联对象类型
     * @param targetIds   关联对象 ID 列表（逗号分隔由 Feign 序列化为多个参数，
     *                    故用 List 由 Spring 默认转换成 {@code targetIds=1&targetIds=2}）
     * @param isPrimary   是否主空间：1 是 0 否（null 视为 0）
     * @param clearOthers 是否先清空这些对象在其他空间的同类关联（设备挪位置=true）
     * @param remark      关联备注（可空）
     * @return 实际生效的关联条数
     */
    @PostMapping("/space-bind")
    R<Integer> bindSpaceRelations(@RequestParam("spaceId") Long spaceId,
        @RequestParam("targetType") String targetType,
        @RequestParam("targetIds") List<Long> targetIds,
        @RequestParam(value = "isPrimary", required = false) Integer isPrimary,
        @RequestParam(value = "clearOthers", required = false) Boolean clearOthers,
        @RequestParam(value = "remark", required = false) String remark);

    /**
     * 把一批业务对象从指定空间解绑。
     *
     * <p>业务侧删除对象（如删除设备）时<b>必须</b>调本端点清理关联：多态关联没有数据库外键，
     * 不清理会留下指向已删对象的幽灵行——表现为空间关联数虚高、且该空间永远删不掉。</p>
     *
     * @param spaceId    空间 ID
     * @param targetType 关联对象类型
     * @param targetIds  关联对象 ID 列表
     * @return 实际删除条数
     */
    @PostMapping("/space-unbind")
    R<Integer> unbindSpaceRelations(@RequestParam("spaceId") Long spaceId,
        @RequestParam("targetType") String targetType,
        @RequestParam("targetIds") List<Long> targetIds);
}
