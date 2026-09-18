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

import cn.ypbin.admin.system.entity.SysTrackEvent;
import cn.ypbin.admin.system.mapper.SysTrackEventMapper;
import cn.ypbin.admin.system.model.query.TrackEventQuery;
import cn.ypbin.admin.system.model.resp.TrackEventResp;
import cn.ypbin.admin.system.model.resp.TrackAppCountResp;
import cn.ypbin.admin.system.model.resp.TrackOverviewResp;
import cn.ypbin.admin.system.model.resp.TrackTopEventResp;
import cn.ypbin.admin.system.model.resp.TrackTrendResp;
import cn.ypbin.admin.system.model.vo.TrackEventExportVo;
import cn.ypbin.admin.system.service.TrackEventService;
import cn.ypbin.admin.system.service.support.TrackQueryParams;
import cn.ypbin.admin.system.service.support.TrackTrendFiller;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.excel.util.ExcelUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 埋点事件查询服务实现（只读）。
 *
 * <p>本服务只负责<strong>查询</strong>：写入由采集链路的消费者线程经 {@code SysTrackEventSink} 完成，
 * 两者不共享任何状态。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Service
public class TrackEventServiceImpl extends BaseServiceImpl<SysTrackEventMapper, SysTrackEvent>
    implements TrackEventService {

    /** 结果标识：成功 */
    private static final int SUCCESS_FLAG = 1;

    /** 导出结果文案：成功 */
    private static final String STATUS_SUCCESS_TEXT = "成功";

    /** 导出结果文案：失败 */
    private static final String STATUS_FAIL_TEXT = "失败";

    /** 导出结果文案：无结果语义 */
    private static final String STATUS_UNKNOWN_TEXT = "-";

    /**
     * 单次导出条数上限。
     *
     * <p>超过即<strong>显式报错</strong>而不是静默截断：静默截断会让使用者以为导出了全量数据。</p>
     */
    private static final long EXPORT_LIMIT = 20000L;

    @Override
    public PageResult<TrackEventResp> pageEvents(TrackEventQuery query) {
        PageResult<SysTrackEvent> source = page(query, buildWrapper(query));
        List<TrackEventResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    public List<TrackTrendResp> eventTrend(int days) {
        TrackQueryParams.requireDays(days);
        LocalDate today = LocalDate.now();
        List<TrackTrendResp> rows = baseMapper.selectDailyTrend(statisticStart(days));
        return TrackTrendFiller.fill(days, today, TrackTrendFiller.indexByDate(rows));
    }

    @Override
    public List<TrackTopEventResp> topEvents(int days, int limit) {
        TrackQueryParams.requireDays(days);
        TrackQueryParams.requireLimit(limit);
        return baseMapper.selectTopEvents(statisticStart(days), limit);
    }

    @Override
    public List<TrackAppCountResp> appDistribution(int days) {
        TrackQueryParams.requireDays(days);
        return baseMapper.selectAppDistribution(statisticStart(days));
    }

    /**
     * 统计窗口起点（含当天）。
     *
     * @param days 天数（调用方已校验）
     * @return 起始时间
     */
    private LocalDateTime statisticStart(int days) {
        return LocalDate.now().minusDays(days - 1L).atStartOfDay();
    }

    @Override
    public TrackOverviewResp overview() {
        LocalDate today = LocalDate.now();
        TrackOverviewResp overview = baseMapper.selectOverview(today.atStartOfDay(),
            today.minusDays(6L).atStartOfDay());
        if (overview == null) {
            // 聚合查询必然返回一行；返回 null 说明 SQL 或映射出了问题，不静默造一个"全零"结果
            throw new BusinessException("埋点概览统计未返回结果，请检查 sys_track_event 的聚合查询");
        }
        return overview;
    }

    @Override
    public void exportEvents(TrackEventQuery query, HttpServletResponse response) {
        LambdaQueryWrapper<SysTrackEvent> wrapper = buildWrapper(query);
        long total = count(wrapper);
        if (total > EXPORT_LIMIT) {
            throw new BusinessException("导出条数为 " + total + "，超过上限 " + EXPORT_LIMIT
                + "，请缩小时间范围或补充筛选条件");
        }
        // 刻意不用分页查询：分页插件有 500 条上限，会把导出静默截断
        List<SysTrackEvent> rows = list(wrapper);
        List<TrackEventExportVo> list = rows.stream().map(this::toExportVo).toList();
        ExcelUtils.export(response, "埋点事件", TrackEventExportVo.class, list);
    }

    private LambdaQueryWrapper<SysTrackEvent> buildWrapper(TrackEventQuery query) {
        return new LambdaQueryWrapper<SysTrackEvent>()
            .eq(StringUtils.hasText(query.getEventCode()), SysTrackEvent::getEventCode, query.getEventCode())
            .eq(StringUtils.hasText(query.getAppId()), SysTrackEvent::getAppId, query.getAppId())
            .eq(query.getUserId() != null, SysTrackEvent::getUserId, query.getUserId())
            .eq(StringUtils.hasText(query.getSessionId()), SysTrackEvent::getSessionId, query.getSessionId())
            .eq(StringUtils.hasText(query.getTraceId()), SysTrackEvent::getTraceId, query.getTraceId())
            .eq(query.getSuccess() != null, SysTrackEvent::getSuccess, query.getSuccess())
            .ge(StringUtils.hasText(query.getStartTime()), SysTrackEvent::getReceivedTime, query.getStartTime())
            .le(StringUtils.hasText(query.getEndTime()), SysTrackEvent::getReceivedTime, query.getEndTime())
            .orderByDesc(SysTrackEvent::getReceivedTime);
    }

    private TrackEventResp toResp(SysTrackEvent event) {
        TrackEventResp resp = new TrackEventResp();
        BeanUtils.copyProperties(event, resp);
        return resp;
    }

    private TrackEventExportVo toExportVo(SysTrackEvent event) {
        TrackEventExportVo vo = new TrackEventExportVo();
        vo.setEventCode(event.getEventCode());
        vo.setAppId(event.getAppId());
        vo.setUserId(event.getUserId());
        vo.setSessionId(event.getSessionId());
        vo.setPageUrl(event.getPageUrl());
        vo.setDurationMs(event.getDurationMs());
        vo.setStatus(toStatusText(event.getSuccess()));
        vo.setReceivedTime(event.getReceivedTime());
        return vo;
    }

    private String toStatusText(Integer success) {
        if (success == null) {
            return STATUS_UNKNOWN_TEXT;
        }
        return SUCCESS_FLAG == success ? STATUS_SUCCESS_TEXT : STATUS_FAIL_TEXT;
    }
}
