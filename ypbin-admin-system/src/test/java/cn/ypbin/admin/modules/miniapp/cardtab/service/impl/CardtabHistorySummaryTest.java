/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoomMember;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabExpenseMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomEventMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabFrequentPlayerResp;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabHistorySummaryResp;
import cn.ypbin.admin.modules.miniapp.cardtab.support.CardtabRoomAccessSupport;
import cn.ypbin.admin.modules.miniapp.common.ws.RoomSocketRegistry;
import cn.ypbin.admin.modules.miniapp.common.ws.WsTicketService;
import cn.ypbin.admin.modules.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 牌账清-历史月度统计与常用玩家聚合测试（不依赖数据库与 Spring 上下文）。
 *
 * <p>这两个接口是「我的」与「历史」页的数据源：月份口径错了会统计到别的月份，
 * 常用玩家把已退出成员或自己算进去则直接展示错人。都是纯聚合逻辑，适合用 mock 覆盖边界。</p>
 *
 * @author wenbin
 * @since 2026-11-13
 */
class CardtabHistorySummaryTest {

    private static final Long ME = 1L;

    private CardtabRoomMemberMapper memberMapper;
    private CardtabExpenseMapper expenseMapper;
    private CardtabRoomAccessSupport accessSupport;
    private CardtabRoomServiceImpl service;

    @BeforeEach
    void setUp() {
        memberMapper = mock(CardtabRoomMemberMapper.class);
        expenseMapper = mock(CardtabExpenseMapper.class);
        accessSupport = mock(CardtabRoomAccessSupport.class);
        when(accessSupport.currentUserId()).thenReturn(ME);
        service = spy(new CardtabRoomServiceImpl(memberMapper, mock(CardtabRoomEventMapper.class),
            expenseMapper, mock(SysUserService.class), accessSupport, mock(WsTicketService.class),
            mock(RoomSocketRegistry.class)));
    }

    private CardtabRoom room(long id, LocalDateTime createTime) {
        CardtabRoom room = new CardtabRoom();
        room.setId(id);
        room.setCreateTime(createTime);
        return room;
    }

    private CardtabRoomMember member(long roomId, long userId, String status) {
        CardtabRoomMember m = new CardtabRoomMember();
        m.setRoomId(roomId);
        m.setUserId(userId);
        m.setMemberStatus(status);
        m.setNickname("u" + userId);
        m.setTotalScore(BigDecimal.ZERO);
        return m;
    }

    private Map<String, Object> expenseRow(long roomId, int count) {
        Map<String, Object> row = new HashMap<>();
        row.put("roomId", roomId);
        row.put("expenseCount", count);
        return row;
    }

    @Test
    @DisplayName("默认月份只统计本月创建的房间，支出笔数按房间汇总")
    void historySummaryCountsCurrentMonth() {
        LocalDateTime now = LocalDateTime.now();
        when(memberMapper.selectList(any())).thenReturn(List.of(member(10L, ME, "IN_ROOM"), member(11L, ME, "LEFT")));
        doReturn(List.of(room(10L, now), room(11L, now.minusMonths(2))))
            .when(service).listByIds(any());
        when(expenseMapper.countByRoomIds(anyList())).thenReturn(List.of(expenseRow(10L, 3)));

        CardtabHistorySummaryResp resp = service.historySummary(null);

        assertThat(resp.getRoomCount()).isEqualTo(1);
        assertThat(resp.getExpenseCount()).isEqualTo(3);
        assertThat(resp.getMonth()).hasSize(7);
    }

    @Test
    @DisplayName("指定月份时按该自然月统计")
    void historySummaryUsesGivenMonth() {
        when(memberMapper.selectList(any())).thenReturn(List.of(member(10L, ME, "IN_ROOM")));
        doReturn(List.of(room(10L, LocalDateTime.of(2026, 10, 5, 20, 0))))
            .when(service).listByIds(any());
        when(expenseMapper.countByRoomIds(anyList())).thenReturn(List.of(expenseRow(10L, 7)));

        CardtabHistorySummaryResp resp = service.historySummary("2026-10");

        assertThat(resp.getMonth()).isEqualTo("2026-10");
        assertThat(resp.getRoomCount()).isEqualTo(1);
        assertThat(resp.getExpenseCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("月份格式非法直接报错，不静默回退到当前月")
    void historySummaryRejectsInvalidMonth() {
        assertThatThrownBy(() -> service.historySummary("2026-13"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("yyyy-MM");
    }

    @Test
    @DisplayName("没有任何房间时返回 0 而不是报错")
    void historySummaryHandlesEmpty() {
        when(memberMapper.selectList(any())).thenReturn(List.of());

        CardtabHistorySummaryResp resp = service.historySummary(null);

        assertThat(resp.getRoomCount()).isZero();
        assertThat(resp.getExpenseCount()).isZero();
    }

    @Test
    @DisplayName("常用玩家排除自己与已退出成员，并按同房次数降序")
    void frequentPlayersExcludesSelfAndLeft() {
        Page<CardtabRoomMember> page = new Page<>(1, 200);
        page.setRecords(List.of(member(10L, ME, "IN_ROOM"), member(11L, ME, "IN_ROOM")));
        when(memberMapper.selectPage(any(), any())).thenReturn(page);
        // 排除自己与 LEFT 由 SQL 条件完成（mock 不执行 wrapper），这里只返回查询实际会命中的行
        when(memberMapper.selectList(any())).thenReturn(List.of(
            member(10L, 2L, "IN_ROOM"),
            member(11L, 2L, "IN_ROOM"),
            member(10L, 3L, "IN_ROOM")));

        List<CardtabFrequentPlayerResp> players = service.frequentPlayers(4);

        assertThat(players).hasSize(2);
        assertThat(players.getFirst().getUserId()).isEqualTo(2L);
        assertThat(players.getFirst().getCount()).isEqualTo(2);
        assertThat(players.get(1).getUserId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("limit 会夹到 1~20，且没有房间时返回空列表")
    void frequentPlayersHonoursLimitAndEmpty() {
        Page<CardtabRoomMember> emptyPage = new Page<>(1, 200);
        emptyPage.setRecords(List.of());
        when(memberMapper.selectPage(any(), any())).thenReturn(emptyPage);
        assertThat(service.frequentPlayers(100)).isEmpty();

        Page<CardtabRoomMember> page = new Page<>(1, 200);
        page.setRecords(List.of(member(10L, ME, "IN_ROOM")));
        when(memberMapper.selectPage(any(), any())).thenReturn(page);
        when(memberMapper.selectList(any())).thenReturn(List.of(
            member(10L, 2L, "IN_ROOM"), member(10L, 3L, "IN_ROOM"), member(10L, 4L, "IN_ROOM")));

        assertThat(service.frequentPlayers(1)).hasSize(1);
    }
}
