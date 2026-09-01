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
import cn.ypbin.admin.system.model.resp.LogTrendResp;
import cn.ypbin.admin.system.model.vo.LogExportVo;
import cn.ypbin.admin.system.service.SysLogService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.excel.util.ExcelUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /** 操作成功标识（与 LogRecord.success 语义一致） */
    private static final int SUCCESS_FLAG = 1;

    /** 导出状态文案：成功 */
    private static final String STATUS_SUCCESS_TEXT = "成功";

    /** 导出状态文案：失败 */
    private static final String STATUS_FAIL_TEXT = "失败";

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

    @Override
    public void exportLogs(LogQuery query, HttpServletResponse response) {
        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<SysLog>()
            .eq(StringUtils.hasText(query.getModule()), SysLog::getModule, query.getModule())
            .like(StringUtils.hasText(query.getDescription()), SysLog::getDescription, query.getDescription())
            .eq(query.getSuccess() != null, SysLog::getSuccess, query.getSuccess())
            .eq(query.getOperateUserId() != null, SysLog::getOperateUserId, query.getOperateUserId())
            .ge(StringUtils.hasText(query.getStartTime()), SysLog::getOperateTime, query.getStartTime())
            .le(StringUtils.hasText(query.getEndTime()), SysLog::getOperateTime, query.getEndTime())
            .orderByDesc(SysLog::getOperateTime);

        List<SysLog> logs = list(wrapper);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<LogExportVo> list = logs.stream().map(l -> {
            LogExportVo vo = new LogExportVo();
            vo.setModule(l.getModule());
            vo.setValue(l.getDescription());
            vo.setOperatorName(l.getOperateUserId() != null ? String.valueOf(l.getOperateUserId()) : "");
            vo.setIp(l.getIp());
            vo.setLocation(l.getLocation());
            vo.setStatus(Integer.valueOf(SUCCESS_FLAG).equals(l.getSuccess())
                ? STATUS_SUCCESS_TEXT : STATUS_FAIL_TEXT);
            vo.setCostTime(l.getTimeTaken());
            vo.setCreateTime(l.getOperateTime() != null ? l.getOperateTime().format(formatter) : "");
            return vo;
        }).toList();

        ExcelUtils.export(response, "系统操作日志", LogExportVo.class, list);
    }

    @Override
    public List<LogResp> latestLogs(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException("日志条数必须在 1 到 100 之间");
        }
        List<SysLog> source = list(new LambdaQueryWrapper<SysLog>()
            .orderByDesc(SysLog::getOperateTime)
            .last("LIMIT " + limit));
        return source.stream().map(this::toResp).toList();
    }

    @Override
    public List<LogTrendResp> logTrend(int days) {
        if (days < 1 || days > 90) {
            throw new BusinessException("统计天数必须在 1 到 90 之间");
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        // 聚合结果按日期建索引，缺失的日期后续补零
        Map<String, Long> countByDate = baseMapper.selectDailyTrend(startDate.atStartOfDay()).stream()
            .collect(Collectors.toMap(LogTrendResp::getDate, LogTrendResp::getCount));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<LogTrendResp> result = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            String date = startDate.plusDays(i).format(formatter);
            LogTrendResp item = new LogTrendResp();
            item.setDate(date);
            item.setCount(countByDate.getOrDefault(date, 0L));
            result.add(item);
        }
        return result;
    }

    private LogResp toResp(SysLog log) {
        LogResp resp = new LogResp();
        BeanUtils.copyProperties(log, resp);
        return resp;
    }
}
