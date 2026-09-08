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

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoomEvent;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoomMember;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabSettlementSnapshot;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomEventMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabSettlementSnapshotMapper;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabSettlementResp;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabSettlementService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 牌账清-结算服务实现。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class CardtabSettlementServiceImpl extends BaseServiceImpl<CardtabSettlementSnapshotMapper, CardtabSettlementSnapshot>
    implements CardtabSettlementService {

    private final CardtabRoomMapper roomMapper;
    private final CardtabRoomMemberMapper memberMapper;
    private final CardtabRoomEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    @Override
    public CardtabSettlementResp getSettlement(Long roomId) {
        CardtabRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }

        // 若已结算且已有快照，直接反序列化快照
        if ("SETTLED".equals(room.getRoomStatus())) {
            CardtabSettlementSnapshot snapshot = getOne(new LambdaQueryWrapper<CardtabSettlementSnapshot>()
                .eq(CardtabSettlementSnapshot::getRoomId, roomId)
                .orderByDesc(CardtabSettlementSnapshot::getId), false);
            if (snapshot != null) {
                try {
                    return objectMapper.readValue(snapshot.getSnapshotJson(), CardtabSettlementResp.class);
                } catch (Exception ignored) {
                }
            }
        }

        // 实时计算
        return calculateSettlement(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardtabSettlementResp saveSettlement(Long roomId) {
        CardtabRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }

        CardtabSettlementResp resp = calculateSettlement(room);
        resp.setStatus("SETTLED");

        // 更新房间状态
        room.setRoomStatus("SETTLED");
        room.setSettledAt(LocalDateTime.now());
        roomMapper.updateById(room);

        // 存储快照
        try {
            CardtabSettlementSnapshot snapshot = new CardtabSettlementSnapshot();
            snapshot.setRoomId(roomId);
            snapshot.setSnapshotJson(objectMapper.writeValueAsString(resp));
            save(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessException("序列化结算快照失败");
        }

        // 记录流水
        CardtabRoomEvent event = new CardtabRoomEvent();
        event.setRoomId(roomId);
        event.setEventType("ROOM_SETTLED");
        event.setTitle("房间已结算");
        event.setContent("房间已完成结算并锁定账目");
        event.setAmount(BigDecimal.ZERO);
        eventMapper.insert(event);

        return resp;
    }

    private CardtabSettlementResp calculateSettlement(CardtabRoom room) {
        List<CardtabRoomMember> members = memberMapper.selectList(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getRoomId, room.getId()));

        CardtabSettlementResp resp = new CardtabSettlementResp();
        resp.setRoomId(room.getId());
        resp.setStatus(room.getRoomStatus());

        if (CollectionUtils.isEmpty(members)) {
            resp.setRankings(Collections.emptyList());
            resp.setTransfers(Collections.emptyList());
            resp.setShareText("本局暂无成员");
            return resp;
        }

        // 1. 排名计算（按总分从高到低）
        List<CardtabRoomMember> sorted = new ArrayList<>(members);
        sorted.sort((a, b) -> b.getTotalScore().compareTo(a.getTotalScore()));

        List<CardtabSettlementResp.RankingItem> rankings = new ArrayList<>();
        int rankNo = 1;
        for (CardtabRoomMember m : sorted) {
            CardtabSettlementResp.RankingItem item = new CardtabSettlementResp.RankingItem();
            item.setMemberId(m.getId());
            item.setNickname(m.getNickname());
            item.setTotalScore(m.getTotalScore());
            item.setRankNo(rankNo++);
            rankings.add(item);
        }
        resp.setRankings(rankings);

        // 2. 转账计算（贪心匹配）
        List<BalanceHolder> creditors = new ArrayList<>(); // 正分
        List<BalanceHolder> debtors = new ArrayList<>();   // 负分

        for (CardtabRoomMember m : members) {
            BigDecimal score = m.getTotalScore();
            if (score.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new BalanceHolder(m.getId(), m.getNickname(), score));
            } else if (score.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new BalanceHolder(m.getId(), m.getNickname(), score.abs()));
            }
        }

        creditors.sort(Comparator.comparing(BalanceHolder::getAmount).reversed());
        debtors.sort(Comparator.comparing(BalanceHolder::getAmount).reversed());

        List<CardtabSettlementResp.TransferItem> transfers = new ArrayList<>();
        int cIdx = 0;
        int dIdx = 0;

        while (cIdx < creditors.size() && dIdx < debtors.size()) {
            BalanceHolder c = creditors.get(cIdx);
            BalanceHolder d = debtors.get(dIdx);

            BigDecimal transferAmount = c.getAmount().min(d.getAmount());
            if (transferAmount.compareTo(BigDecimal.ZERO) > 0) {
                CardtabSettlementResp.TransferItem t = new CardtabSettlementResp.TransferItem();
                t.setFromMemberId(d.getMemberId());
                t.setFromName(d.getNickname());
                t.setToMemberId(c.getMemberId());
                t.setToName(c.getNickname());
                t.setAmount(transferAmount);
                transfers.add(t);

                c.setAmount(c.getAmount().subtract(transferAmount));
                d.setAmount(d.getAmount().subtract(transferAmount));
            }

            if (c.getAmount().compareTo(BigDecimal.ZERO) <= 0) cIdx++;
            if (d.getAmount().compareTo(BigDecimal.ZERO) <= 0) dIdx++;
        }
        resp.setTransfers(transfers);

        // 3. 生成分享文案
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(room.getName()).append("】结算账单：\n");
        for (CardtabSettlementResp.RankingItem r : rankings) {
            sb.append("  ").append(r.getRankNo()).append(". ").append(r.getNickname()).append("：")
                .append(r.getTotalScore().compareTo(BigDecimal.ZERO) > 0 ? "+" : "")
                .append(r.getTotalScore()).append("\n");
        }
        if (!transfers.isEmpty()) {
            sb.append("\n转账建议：\n");
            for (CardtabSettlementResp.TransferItem t : transfers) {
                sb.append("  ").append(t.getFromName()).append(" -> ").append(t.getToName())
                    .append("：").append(t.getAmount()).append("\n");
            }
        }
        resp.setShareText(sb.toString().trim());

        return resp;
    }

    private static class BalanceHolder {
        private final Long memberId;
        private final String nickname;
        private BigDecimal amount;

        public BalanceHolder(Long memberId, String nickname, BigDecimal amount) {
            this.memberId = memberId;
            this.nickname = nickname;
            this.amount = amount;
        }

        public Long getMemberId() {
            return memberId;
        }

        public String getNickname() {
            return nickname;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
    }
}

