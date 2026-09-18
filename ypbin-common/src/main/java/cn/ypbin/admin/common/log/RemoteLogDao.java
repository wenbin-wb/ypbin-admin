/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.log;

import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.model.LogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志持久化端口（跨服务上报实现）：把采集完成的 {@link LogRecord} 上报给 system 服务落 {@code sys_log}。
 *
 * <p><strong>为什么是上报而不是直连库：</strong>auth 无数据源，ai 的数据源属于 AI 自己的库表域；
 * 跨服务数据访问必须走 {@link ISystemClient}（{@code .claude/skills/ypbin-admin-dev/SKILL.md:20-21}：
 * "新增跨服务数据访问，先扩展 {@code ISystemClient} + {@code SystemClientImpl}，禁止在调用方服务加 Mapper 直连"）。
 * 本类因此只做搬运：不做任何字段改名映射，{@code sys_log} 的映射全仓只有
 * {@code DbLogProviders.DbLogDao} 一份。</p>
 *
 * <p><strong>为什么失败要显式记堆栈并上抛：</strong>system 不可达时 {@code ISystemClientFallback}
 * 返回失败 {@code R}（{@code code=500}），若此处静默 return，就是"登录日志没落库且无任何痕迹"的静默降级。
 * 这里<b>不依赖</b> starter 的 {@code LogEventListener} 来记堆栈——本仓固定的 starter 版本
 * {@code ypbin-starter-log:3.3.0} 的监听器只打 {@code log.warn("... persist failed: {}", e.getMessage())}，
 * <b>不带 Throwable</b>（starter 侧补堆栈的提交 {@code 0dc6ef1} 在 v3.3.0 之后，见
 * {@code v3.3.0} 的 {@code LogEventListener} 源码），因此"完整堆栈"必须在本类落实。
 * 记完再上抛，监听器仍会捕获（异常不逃逸到业务线程，登录活动照常返回）。</p>
 *
 * <p><strong>调用线程：</strong>本方法由 {@code LogEventListener.onLogEvent} 调用，该方法带 {@code @Async}，
 * 故实际执行在异步线程；{@code LogRecord} 的采集（{@code LogCollector.collect}）已发生在业务线程，
 * 落库所需字段此时均已就绪。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
public class RemoteLogDao implements LogDao {

    private static final Logger log = LoggerFactory.getLogger(RemoteLogDao.class);

    private final ISystemClient systemClient;

    public RemoteLogDao(ISystemClient systemClient) {
        this.systemClient = systemClient;
    }

    @Override
    public void add(LogRecord logRecord) {
        R<Void> result;
        try {
            result = systemClient.ingestLog(logRecord);
        } catch (RuntimeException ex) {
            // 传输层失败：连接/读取超时、熔断、响应解码异常。记完整堆栈后原样上抛（保留原始栈）
            log.error("[ypbin-admin] 操作日志上报 system 服务失败（传输异常），本条日志未落库: "
                + "module={}, description={}, requestUri={}",
                logRecord.getModule(), logRecord.getDescription(), logRecord.getRequestUri(), ex);
            throw ex;
        }
        if (result == null || !result.isSuccess()) {
            // 业务码失败（/internal 凭证不匹配 401、system 落库异常 500、降级兜底 500）没有异常对象，
            // 显式构造以产出可定位的堆栈
            IllegalStateException failure = new IllegalStateException(
                "[ypbin-admin] 操作日志上报 system 服务失败，本条日志未落库: "
                    + "code=" + (result == null ? "null" : result.getCode())
                    + ", message=" + (result == null ? "响应为空" : result.getMessage()));
            log.error("[ypbin-admin] 操作日志上报 system 服务失败（业务码）, module={}, description={}, requestUri={}",
                logRecord.getModule(), logRecord.getDescription(), logRecord.getRequestUri(), failure);
            throw failure;
        }
    }
}
