/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import cn.ypbin.admin.system.entity.SysLog;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 操作日志持久化与操作人数据源（数据库实现）。
 *
 * <p>starter 的 log 模块把扩展点定义得很清楚，落库需要宿主提供的是 <strong>{@link LogDao}</strong> 而
 * <strong>不是</strong> {@code LogCollector}：{@code LogCollector} 是 starter 自带的具体采集器 Bean
 * （starter 3.3.0 {@code LogAutoConfiguration.java:89-96} 创建，负责从 Servlet 请求采集元信息），
 * 宿主可覆盖的持久化端口是 {@code LogAutoConfiguration.java:61-65} 的 {@code LogDao}——其默认实现
 * {@code DefaultLogDao} 只把日志打印到 {@code ypbin.access-log} 应用日志，<strong>不写库，也不报错</strong>，
 * 这正是此前 {@code sys_log} 恒为空的原因。同理操作人来自 {@code LogUserProvider}
 * （{@code LogAutoConfiguration.java:67-71} 默认返回空），本类一并接上。</p>
 *
 * <p><strong>落库时机：</strong>{@code LogAspect.around} 在方法返回后的 {@code finally} 中采集并
 * {@code publishEvent(new LogEvent(record))}（starter 3.3.0 {@code LogAspect.java:87-98}），
 * {@code LogEventListener.onLogEvent} 再调 {@code LogDao.add}（{@code LogEventListener.java:43-51}）。
 * 注意该监听器虽标了 {@code @Async}，但 <strong>本仓未引入 {@code ypbin-starter-async}，
 * {@code @EnableAsync} 不生效</strong>（依赖树已核实：{@code mvn -pl ypbin-service/ypbin-system
 * dependency:tree} 无 {@code ypbin-starter-async}），因此当前实际上在请求线程内同步落库。</p>
 *
 * <p><strong>租户上下文：</strong>{@code sys_log} 已登记在 {@code ypbin.tenant.ignore-tables}
 * （{@code deploy/nacos/ypbin-system.yaml:24}），租户拦截器不为其追加任何条件，故落库不需要租户上下文，
 * 也就不会因 {@code fail-on-missing-tenant: true} 被拒。{@code SysLog} 因此不继承 {@code BaseEntity}
 * （见其类注释），本类也无需做租户透传。</p>
 *
 * <p><strong>已知边界：</strong>① {@code LogRecord.requestHeaders/responseHeaders} 在 {@code sys_log}
 * 无对应列，直接丢弃；② {@code clientId/clientType/authType} 由 {@code LogClientProvider} 提供，
 * 本仓实现是 {@code ypbin-common} 的 {@code SessionLogClientProvider}（读登录会话中的
 * {@code LoginUser}，不扩展网关身份头契约），auth/system/ai 三服务共用同一份实现；若某服务未装配
 * provider，starter 默认实现恒返回空，这三列即为空；③ {@code location} 依赖
 * {@code IpLocationResolver}，本仓未接入离线 IP 库，故留空而非臆造。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 * <p><strong>与依赖版本相关的事实（务必知道）</strong>：本仓固定 {@code ypbin-starter-log:3.3.0}，
 * 该版本的 {@code LogEventListener} 在落库失败时<strong>只打印 message、不带堆栈</strong>
 * （starter 侧补堆栈的改动晚于 v3.3.0，需等发版才生效）。因此本类与上报侧
 * （{@code RemoteLogDao}）<strong>自己</strong>负责抛错/记完整堆栈，不能依赖监听器。</p>
 */
public final class DbLogProviders {

    private DbLogProviders() {
    }

    /**
     * 操作日志落库：{@link LogRecord} → {@code sys_log}。
     *
     * <p>字段同名映射，不做改名（DB=实体=采集字段）。异常不在此吞掉，交由 starter 的
     * {@code LogEventListener} 记录，避免掩盖落库失败。</p>
     */
    @Component
    @RequiredArgsConstructor
    public static class DbLogDao implements LogDao {

        /** 成功标志（与 sys_log.success 列注释一致：1 是 0 否） */
        private static final int SUCCESS_FLAG = 1;

        /** 失败标志 */
        private static final int FAIL_FLAG = 0;

        private final SysLogMapper logMapper;

        @Override
        public void add(LogRecord logRecord) {
            SysLog entity = new SysLog();
            entity.setDescription(logRecord.getDescription());
            entity.setModule(logRecord.getModule());
            entity.setRequestMethod(logRecord.getRequestMethod());
            entity.setRequestUri(logRecord.getRequestUri());
            entity.setRequestParam(logRecord.getRequestParam());
            entity.setRequestBody(logRecord.getRequestBody());
            entity.setResponseBody(logRecord.getResponseBody());
            entity.setStatusCode(logRecord.getStatusCode());
            entity.setIp(logRecord.getIp());
            entity.setLocation(logRecord.getLocation());
            entity.setBrowser(logRecord.getBrowser());
            entity.setOs(logRecord.getOs());
            entity.setClientId(logRecord.getClientId());
            entity.setClientType(logRecord.getClientType());
            entity.setAuthType(logRecord.getAuthType());
            entity.setOperateUserId(logRecord.getUserId());
            entity.setOperateTime(toLocalDateTime(logRecord));
            entity.setTimeTaken(logRecord.getTimeTakenMillis());
            entity.setSuccess(logRecord.isSuccess() ? SUCCESS_FLAG : FAIL_FLAG);
            entity.setErrorMsg(logRecord.getErrorMsg());
            logMapper.insert(entity);
        }

        /**
         * 采集时间（{@code LogRecord.timestamp}，{@code Instant}）转库内 {@link LocalDateTime}，
         * 按 JVM 时区换算。
         */
        private LocalDateTime toLocalDateTime(LogRecord logRecord) {
            return logRecord.getTimestamp() == null
                ? null
                : LocalDateTime.ofInstant(logRecord.getTimestamp(), ZoneId.systemDefault());
        }
    }

    /**
     * 操作人数据源：微服务版当前用户来自网关签发的身份头（{@code IdentityContext}），
     * 与 {@code MicroserviceTenantProvider} 的取法一致，不读 sa-token 会话。
     */
    @Component
    public static class IdentityLogUserProvider implements LogUserProvider {

        @Override
        public Optional<Long> getCurrentUserId() {
            return IdentityContext.getUserId();
        }
    }
}
