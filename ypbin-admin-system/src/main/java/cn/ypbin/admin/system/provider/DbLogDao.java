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
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.model.LogRecord;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 日志持久化实现：将 starter 采集的 {@link LogRecord} 落库到 sys_log。
 *
 * <p>starter 的 @Log 已异步发布日志事件，本方法在异步线程执行插入，不阻塞业务请求。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Component
@RequiredArgsConstructor
public class DbLogDao implements LogDao {

    private final SysLogMapper logMapper;

    @Override
    public void add(LogRecord record) {
        SysLog log = new SysLog();
        log.setDescription(record.getDescription());
        log.setModule(record.getModule());
        log.setRequestMethod(record.getRequestMethod());
        log.setRequestUri(record.getRequestUri());
        log.setRequestParam(record.getRequestParam());
        log.setRequestBody(record.getRequestBody());
        log.setResponseBody(record.getResponseBody());
        log.setStatusCode(record.getStatusCode());
        log.setIp(record.getIp());
        log.setLocation(record.getLocation());
        log.setBrowser(record.getBrowser());
        log.setOs(record.getOs());
        log.setClientId(record.getClientId());
        log.setClientType(record.getClientType());
        log.setAuthType(record.getAuthType());
        log.setOperateUserId(record.getUserId());
        if (record.getTimestamp() != null) {
            log.setOperateTime(LocalDateTime.ofInstant(record.getTimestamp(), ZoneId.systemDefault()));
        }
        log.setTimeTaken(record.getTimeTakenMillis());
        log.setSuccess(record.isSuccess() ? 1 : 0);
        log.setErrorMsg(record.getErrorMsg());
        logMapper.insert(log);
    }
}
