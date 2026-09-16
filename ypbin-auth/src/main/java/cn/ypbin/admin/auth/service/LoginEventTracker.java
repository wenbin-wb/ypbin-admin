/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.service;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackRequestContext;
import cn.ypbin.starter.tracking.core.TrackingEventCodes;
import cn.ypbin.starter.web.util.WebRequestUtils;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 登录/登出埋点上报（auth 侧唯一的埋点发送方）。
 *
 * <p><strong>为什么用 {@link TrackRecorder} 而不是 {@code @Tracked} 注解：</strong></p>
 * <ol>
 *   <li><strong>注解带不了属性</strong>：{@code auth.user.login} 在事件目录里声明的属性只有
 *       {@code authType}（{@code META-INF/ypbin/tracking-events.json:30-42}），而
 *       {@code @Tracked} 只有 {@code value()} 一个成员、明确写明「本注解不携带事件属性」
 *       （{@code Tracked.java:37-53}）；用注解就拿不到「账号/短信/第三方」这个区分维度。</li>
 *   <li><strong>注解丢身份维度</strong>：{@code TrackedAspect} 用不带请求上下文的构造器造事件
 *       （{@code TrackedAspect.java:91-92}，构造后 {@code context=null}），于是 IP / UA / 链路 ID /
 *       <b>userId</b> 全为空。登录事件没有 userId 就无法按人做漏斗，等于白采。</li>
 *   <li><strong>注解在消费者线程外无落点</strong>：auth 没有埋点落库实现，注解与门面都要靠
 *       {@code TrackEventSink} 决定去向；本仓的落库实现在 system 服务，故两者都需要
 *       {@code RemoteTrackEventSink} 上报（见 {@code RemoteTrackAutoConfiguration}）。</li>
 * </ol>
 *
 * <p><strong>请求上下文必须在请求线程上捕获</strong>（{@code TrackRequestContext} 的说明）：
 * 落库发生在采集链路的消费者线程上，那时请求早已结束，IP/UA/链路 ID/用户再也补不回来，
 * 所以本类只允许在请求线程调用（登录/登出都在 HTTP 请求内，满足该前提）。</p>
 *
 * <p><strong>绝不影响业务</strong>：埋点是旁路能力，任何失败只记完整堆栈（不静默吞掉），
 * 绝不向登录/登出主流程抛出。{@link TrackRecorder#record} 自身契约不抛异常
 * （{@code TrackRecorder.java:31-32}），但事件构造期会校验必填项
 * （{@code TrackEvent.java:93-99}），故此处必须整体兜底。</p>
 *
 * <p><strong>兜哪些异常</strong>：{@code RuntimeException}（埋点链路自身故障）与
 * {@code LinkageError}（缺类 / 静态初始化失败——可选依赖缺失时的典型形态，
 * {@code NoClassDefFoundError}、{@code ExceptionInInitializerError} 都是它的子类）。
 * <b>刻意不兜</b> {@code VirtualMachineError}（OOM / StackOverflow）与 {@code ThreadDeath}：
 * 那是 JVM 级致命状态，继续执行并打日志既不安全也无意义。
 * <b>整个方法体都在 try 内</b>——包括从容器取 Bean 那一步，因为依赖缺失正是在那里冒出来的。</p>
 *
 * <p><strong>payload 只放白名单属性</strong>：登录仅 {@code authType}（取值 ACCOUNT/PHONE/SOCIAL），
 * 登出为空表；密码、令牌、手机号等敏感值一律不进 payload（也不进日志）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
@RequiredArgsConstructor
public class LoginEventTracker {

    private static final Logger log = LoggerFactory.getLogger(LoginEventTracker.class);

    /** 登录事件的属性名（事件目录声明的白名单键） */
    private static final String PROPERTY_AUTH_TYPE = "authType";

    /** 网关注入的链路 ID 请求头（与 starter 采集侧口径一致：{@code TrackRequestContextResolver:47}） */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 事件结果为成功 */
    private static final boolean SUCCESS = true;

    /**
     * 埋点门面：可选依赖，只有 {@code ypbin.tracking.enabled=true} 时 starter 才装配它。
     *
     * <p>用 {@link ObjectProvider} 而不是直接注入：埋点默认关闭（{@code TrackingAutoConfiguration.java:51}），
     * 直接注入会让「可选能力」变成「服务启动硬依赖」。</p>
     */
    private final ObjectProvider<TrackRecorder> recorderProvider;

    /** 「埋点未启用」只提示一次，避免每次登录都刷屏（计数口径由 starter 的 TrackCounters 负责） */
    private final AtomicBoolean disabledWarned = new AtomicBoolean(false);

    /**
     * 上报登录成功事件（账号密码 / 短信 / 第三方三个入口共用）。
     *
     * @param user      已校验通过的用户（取 userId / tenantId 作事件维度）
     * @param authType  认证方式（ACCOUNT / PHONE / SOCIAL）
     * @param ip        客户端 IP
     * @param userAgent 客户端 User-Agent 原始串，可空
     */
    public void recordLogin(SysUser user, String authType, String ip, @Nullable String userAgent) {
        record(TrackingEventCodes.AUTH_USER_LOGIN, Map.of(PROPERTY_AUTH_TYPE, authType),
            user.getId(), user.getTenantId(), ip, userAgent);
    }

    /**
     * 上报登出事件。
     *
     * <p>必须在销毁会话<b>之前</b>取到 userId/tenantId（调用方负责），会话销毁后身份头与 sa-token 会话都没了。</p>
     *
     * @param userId    当前登录用户 ID，未登录（无会话）时为空
     * @param tenantId  当前租户 ID，可空
     * @param ip        客户端 IP
     * @param userAgent 客户端 User-Agent 原始串，可空
     */
    public void recordLogout(@Nullable Long userId, @Nullable Long tenantId, String ip,
                             @Nullable String userAgent) {
        record(TrackingEventCodes.AUTH_USER_LOGOUT, Map.of(), userId, tenantId, ip, userAgent);
    }

    /**
     * 构造并投递一条事件；任何异常只记堆栈，绝不外抛。
     */
    private void record(String eventCode, Map<String, Object> payload, @Nullable Long userId,
                        @Nullable Long tenantId, String ip, @Nullable String userAgent) {
        try {
            // Bean 解析也在 try 内：可选依赖缺失（NoClassDefFoundError）正是从这一行冒出来的
            TrackRecorder recorder = recorderProvider.getIfAvailable();
            if (recorder == null) {
                warnDisabledOnce();
                return;
            }
            TrackRequestContext context = new TrackRequestContext(normalize(ip), normalize(userAgent),
                resolveTraceId(), userId, tenantId);
            recorder.record(new TrackEvent(UUID.randomUUID().toString(), eventCode, Instant.now(),
                null, null, null, null, null, null, SUCCESS, payload, context));
        } catch (RuntimeException | LinkageError ex) {
            // 只兜「埋点链路故障」与「缺类/静态初始化失败」两族，且仍留完整堆栈；
            // 不兜 VirtualMachineError/ThreadDeath（JVM 级致命，继续执行无意义）
            log.error("[ypbin-admin] 上报埋点事件失败，事件未采集（不影响业务），eventCode={}, userId={}",
                eventCode, userId, ex);
        }
    }

    /**
     * 埋点未启用时给出一次显式提示。
     *
     * <p>这不是静默降级：登录/登出事件是功能开关驱动的可选能力（{@code ypbin.tracking.enabled}），
     * 关闭时不采集是配置语义；但"漏斗里没有登录这一步"必须可被归因，故留下一行 WARN 指明原因与开关名。</p>
     */
    private void warnDisabledOnce() {
        if (disabledWarned.compareAndSet(false, true)) {
            log.warn("[ypbin-admin] 登录/登出埋点未上报：ypbin.tracking.enabled 未开启，"
                + "auth 采集链路未装配（漏斗将缺少 auth.user.login / auth.user.logout 两个步骤）。");
        }
    }

    /**
     * 读取网关注入的链路 ID；无请求上下文（非 HTTP 线程）时留空并留痕。
     *
     * <p>与 {@code AdminTrackIdentityProvider#resolve} 同策略：取不到维度不是错误，
     * 但仍要可见，不能无声吞掉。</p>
     */
    private @Nullable String resolveTraceId() {
        try {
            return normalize(WebRequestUtils.header(REQUEST_ID_HEADER));
        } catch (RuntimeException ex) {
            log.warn("[ypbin-admin] 埋点事件无 HTTP 请求上下文，链路 ID 维度将为空。", ex);
            return null;
        }
    }

    private @Nullable String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
