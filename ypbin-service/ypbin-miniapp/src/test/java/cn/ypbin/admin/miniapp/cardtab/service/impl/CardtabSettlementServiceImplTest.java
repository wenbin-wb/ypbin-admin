/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoomMember;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabSettlementSnapshot;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomEventMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabSettlementSnapshotMapper;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabSettlementResp;
import cn.ypbin.starter.core.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 牌账清-结算计算测试（不依赖数据库与 Spring 上下文）。
 *
 * <p><b>为什么先测这里</b>：结算直接产出「谁该给谁多少钱」，算错就是钱错；而贪心转账化简
 * （creditor/debtor 双指针）与排名是最容易被后续改动碰坏的纯逻辑。该模块此前<b>零单测</b>。</p>
 *
 * <p>覆盖：排名、贪心转账最小化、零分成员不参与转账、无成员、房间不存在、快照命中/快照损坏回退。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
class CardtabSettlementServiceImplTest {

    private static final Long ROOM_ID = 100L;

    private CardtabRoomMapper roomMapper;
    private CardtabRoomMemberMapper memberMapper;
    private CardtabSettlementSnapshotMapper snapshotMapper;
    private CardtabSettlementServiceImpl service;

    @BeforeEach
    void setUp() {
        roomMapper = mock(CardtabRoomMapper.class);
        memberMapper = mock(CardtabRoomMemberMapper.class);
        snapshotMapper = mock(CardtabSettlementSnapshotMapper.class);
        service = new CardtabSettlementServiceImpl(roomMapper, memberMapper,
            mock(CardtabRoomEventMapper.class), new ObjectMapper());
    }

    private CardtabRoom room(String status) {
        CardtabRoom room = new CardtabRoom();
        room.setId(ROOM_ID);
        room.setName("周末牌局");
        room.setRoomStatus(status);
        return room;
    }

    private CardtabRoomMember member(long id, String nickname, String score) {
        CardtabRoomMember m = new CardtabRoomMember();
        m.setId(id);
        m.setRoomId(ROOM_ID);
        m.setNickname(nickname);
        m.setTotalScore(new BigDecimal(score));
        return m;
    }

    @Test
    @DisplayName("结算排名按总分降序、名次连续，正分带 + 号写入分享文案")
    void rankingShouldBeOrderedByScoreDesc() {
        when(roomMapper.selectById(ROOM_ID)).thenReturn(room("ACTIVE"));
        when(memberMapper.selectList(any())).thenReturn(List.of(
            member(1, "阿明", "-40"), member(2, "小红", "100"), member(3, "老王", "-60")));

        CardtabSettlementResp resp = service.getSettlement(ROOM_ID);

        assertThat(resp.getRankings()).extracting(CardtabSettlementResp.RankingItem::getNickname)
            .containsExactly("小红", "阿明", "老王");
        assertThat(resp.getRankings()).extracting(CardtabSettlementResp.RankingItem::getRankNo)
            .containsExactly(1, 2, 3);
        assertThat(resp.getShareText()).contains("小红：+100").contains("阿明：-40");
    }

    @Test
    @DisplayName("贪心转账化简：大额债务人优先配对，笔数与金额都必须精确")
    void transfersShouldBeMinimalGreedyMatch() {
        when(roomMapper.selectById(ROOM_ID)).thenReturn(room("ACTIVE"));
        when(memberMapper.selectList(any())).thenReturn(List.of(
            member(1, "小红", "100"), member(2, "阿明", "-40"), member(3, "老王", "-60")));

        CardtabSettlementResp resp = service.getSettlement(ROOM_ID);

        assertThat(resp.getTransfers()).hasSize(2);
        // 债务人按欠款降序：老王(60) 先配，再是阿明(40)；债权人为小红(100)
        assertThat(resp.getTransfers().getFirst().getFromName()).isEqualTo("老王");
        assertThat(resp.getTransfers().getFirst().getToName()).isEqualTo("小红");
        assertThat(resp.getTransfers().getFirst().getAmount()).isEqualByComparingTo("60");
        assertThat(resp.getTransfers().get(1).getFromName()).isEqualTo("阿明");
        assertThat(resp.getTransfers().get(1).getAmount()).isEqualByComparingTo("40");
        // 转账总额守恒：等于所有欠款之和
        BigDecimal total = resp.getTransfers().stream()
            .map(CardtabSettlementResp.TransferItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("零分成员只参与排名，不产生任何转账")
    void zeroScoreMemberShouldNotAppearInTransfers() {
        when(roomMapper.selectById(ROOM_ID)).thenReturn(room("ACTIVE"));
        when(memberMapper.selectList(any())).thenReturn(List.of(
            member(1, "小红", "100"), member(2, "阿明", "-100"), member(3, "看客", "0")));

        CardtabSettlementResp resp = service.getSettlement(ROOM_ID);

        assertThat(resp.getRankings()).hasSize(3);
        assertThat(resp.getTransfers()).hasSize(1);
        assertThat(resp.getTransfers().getFirst().getFromName()).isEqualTo("阿明");
        assertThat(resp.getTransfers().getFirst().getAmount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("房间无成员时返回空集合（不是 null）并给出提示文案")
    void emptyMembersShouldReturnEmptyCollections() {
        when(roomMapper.selectById(ROOM_ID)).thenReturn(room("ACTIVE"));
        when(memberMapper.selectList(any())).thenReturn(List.of());

        CardtabSettlementResp resp = service.getSettlement(ROOM_ID);

        assertThat(resp.getRankings()).isEmpty();
        assertThat(resp.getTransfers()).isEmpty();
        assertThat(resp.getShareText()).isEqualTo("本局暂无成员");
    }

    @Test
    @DisplayName("房间不存在时显式抛业务异常，不返回空结算单")
    void missingRoomShouldThrow() {
        when(roomMapper.selectById(anyLong())).thenReturn(null);

        assertThatThrownBy(() -> service.getSettlement(ROOM_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("房间不存在");
    }

    @Test
    @DisplayName("已结算且快照可用时直接读快照，不再实时重算")
    void settledRoomWithValidSnapshotShouldUseSnapshot() {
        when(roomMapper.selectById(ROOM_ID)).thenReturn(room("SETTLED"));
        CardtabSettlementSnapshot snapshot = new CardtabSettlementSnapshot();
        snapshot.setId(9L);
        snapshot.setRoomId(ROOM_ID);
        snapshot.setSnapshotJson("{\"roomId\":100,\"status\":\"SETTLED\",\"rankings\":[],"
            + "\"transfers\":[],\"shareText\":\"快照文案\"}");
        // 直接 spy 掉父类的 getOne：本用例测的是「命中快照就不再实时重算」这一分支，
        // 不依赖 MP 内部究竟走 selectOne 还是 selectList（那属于框架实现细节）
        CardtabSettlementServiceImpl spyService = spy(service);
        doReturn(snapshot).when(spyService).getOne(any(), eq(false));

        CardtabSettlementResp resp = spyService.getSettlement(ROOM_ID);

        assertThat(resp.getShareText()).isEqualTo("快照文案");
        verify(memberMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("快照损坏时回退实时计算（行为不变），且不会把异常吞掉导致静默")
    void settledRoomWithBrokenSnapshotShouldFallbackToRealtime() {
        when(roomMapper.selectById(ROOM_ID)).thenReturn(room("SETTLED"));
        CardtabSettlementSnapshot snapshot = new CardtabSettlementSnapshot();
        snapshot.setId(9L);
        snapshot.setSnapshotJson("{ 这不是合法 JSON");
        when(memberMapper.selectList(any())).thenReturn(List.of(
            member(1, "小红", "30"), member(2, "阿明", "-30")));
        CardtabSettlementServiceImpl spyService = spy(service);
        doReturn(snapshot).when(spyService).getOne(any(), eq(false));

        CardtabSettlementResp resp = spyService.getSettlement(ROOM_ID);

        // 回退后仍是完整可用的结算结果（不是空壳）
        assertThat(resp.getStatus()).isEqualTo("SETTLED");
        assertThat(resp.getTransfers()).hasSize(1);
        assertThat(resp.getTransfers().getFirst().getAmount()).isEqualByComparingTo("30");
    }
}
