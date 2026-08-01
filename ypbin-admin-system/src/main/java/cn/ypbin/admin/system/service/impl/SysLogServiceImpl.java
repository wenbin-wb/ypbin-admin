/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.entity.SysLog;
import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.admin.system.model.query.LogQuery;
import cn.ypbin.admin.system.model.resp.LogResp;
import cn.ypbin.admin.system.service.SysLogService;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 系统日志服务实现（只读）。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
public class SysLogServiceImpl extends BaseServiceImpl<SysLogMapper, SysLog> implements SysLogService {

    @Override
    public PageResult<LogResp> pageLogs(LogQuery query) {
        PageResult<SysLog> source = page(query, new LambdaQueryWrapper<SysLog>()
            .eq(StringUtils.hasText(query.getModule()), SysLog::getModule, query.getModule())
            .like(StringUtils.hasText(query.getDescription()), SysLog::getDescription, query.getDescription())
            .eq(query.getSuccess() != null, SysLog::getSuccess, query.getSuccess())
            .eq(query.getOperateUserId() != null, SysLog::getOperateUserId, query.getOperateUserId())
            .ge(StringUtils.hasText(query.getStartTime()), SysLog::getOperateTime, query.getStartTime())
            .le(StringUtils.hasText(query.getEndTime()), SysLog::getOperateTime, query.getEndTime())
            .orderByDesc(SysLog::getOperateTime));
        List<LogResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    private LogResp toResp(SysLog log) {
        LogResp resp = new LogResp();
        BeanUtils.copyProperties(log, resp);
        return resp;
    }
}
