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

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabExpense;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoomEvent;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoomMember;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabExpenseMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomEventMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabExpenseReq;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabExpenseService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.identity.IdentityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 牌账清-支出记录服务实现。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class CardtabExpenseServiceImpl extends BaseServiceImpl<CardtabExpenseMapper, CardtabExpense>
    implements CardtabExpenseService {

    private final CardtabRoomMapper roomMapper;
    private final CardtabRoomMemberMapper memberMapper;
    private final CardtabRoomEventMapper eventMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createExpense(Long roomId, CardtabExpenseReq req) {
        Long userId = currentUserId();
        CardtabRoom room = roomMapper.selectById(roomId);
        if (room == null || !"ONGOING".equals(room.getRoomStatus())) {
            throw new BusinessException("房间不存在或已结算，无法记账");
        }

        // 查找当前用户在房间内的成员身份
        CardtabRoomMember fromMember = memberMapper.selectOne(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getRoomId, roomId)
            .eq(CardtabRoomMember::getUserId, userId));

        if (fromMember == null || "LEFT".equals(fromMember.getMemberStatus())) {
            throw new BusinessException("您未在此房间内，无法记账");
        }

        if (fromMember.getId().equals(req.getToMemberId())) {
            throw new BusinessException("不能给自己记支出");
        }

        CardtabRoomMember toMember = memberMapper.selectById(req.getToMemberId());
        if (toMember == null || !toMember.getRoomId().equals(roomId)) {
            throw new BusinessException("收款成员不存在或不在本房间内");
        }

        BigDecimal amount = req.getAmount();

        // 1. 新增支出记录
        CardtabExpense expense = new CardtabExpense();
        expense.setRoomId(roomId);
        expense.setFromMemberId(fromMember.getId());
        expense.setToMemberId(toMember.getId());
        expense.setAmount(amount);
        expense.setNote(req.getNote());
        expense.setExpenseStatus("NORMAL");
        save(expense);

        // 2. 更新双方积分：fromMember 减分，toMember 加分，房间总和恒为 0
        fromMember.setTotalScore(fromMember.getTotalScore().subtract(amount));
        toMember.setTotalScore(toMember.getTotalScore().add(amount));
        memberMapper.updateById(fromMember);
        memberMapper.updateById(toMember);

        // 3. 记录流水
        CardtabRoomEvent event = new CardtabRoomEvent();
        event.setRoomId(roomId);
        event.setEventType("EXPENSE_CREATED");
        event.setMemberId(fromMember.getId());
        event.setRefId(expense.getId());
        event.setTitle("记一笔支出");
        event.setContent(fromMember.getNickname() + " 给 " + toMember.getNickname() + " 记了一笔 " + amount + (req.getNote() != null ? " (" + req.getNote() + ")" : ""));
        event.setAmount(amount);
        event.setFromMemberName(fromMember.getNickname());
        event.setToMemberName(toMember.getNickname());
        eventMapper.insert(event);

        Map<String, Object> res = new HashMap<>();
        res.put("id", expense.getId());
        res.put("fromMemberId", fromMember.getId());
        res.put("toMemberId", toMember.getId());
        res.put("amount", amount);
        res.put("note", req.getNote());
        res.put("createdAt", expense.getCreateTime());
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeExpense(Long roomId, Long expenseId) {
        Long userId = currentUserId();
        CardtabExpense expense = getById(expenseId);
        if (expense == null || !expense.getRoomId().equals(roomId)) {
            throw new BusinessException("支出记录不存在");
        }
        if ("REVOKED".equals(expense.getExpenseStatus())) {
            throw new BusinessException("该笔支出已撤销");
        }

        CardtabRoomMember fromMember = memberMapper.selectById(expense.getFromMemberId());
        CardtabRoomMember toMember = memberMapper.selectById(expense.getToMemberId());
        if (fromMember == null || toMember == null) {
            throw new BusinessException("关联成员不存在，无法撤销");
        }

        // 状态变更为已撤销
        expense.setExpenseStatus("REVOKED");
        expense.setRevokedAt(LocalDateTime.now());
        updateById(expense);

        // 回滚积分
        fromMember.setTotalScore(fromMember.getTotalScore().add(expense.getAmount()));
        toMember.setTotalScore(toMember.getTotalScore().subtract(expense.getAmount()));
        memberMapper.updateById(fromMember);
        memberMapper.updateById(toMember);

        // 记录流水
        CardtabRoomEvent event = new CardtabRoomEvent();
        event.setRoomId(roomId);
        event.setEventType("EXPENSE_REVOKED");
        event.setMemberId(fromMember.getId());
        event.setRefId(expense.getId());
        event.setTitle("撤销支出");
        event.setContent(fromMember.getNickname() + " 撤销了给 " + toMember.getNickname() + " 的支出 " + expense.getAmount());
        event.setAmount(expense.getAmount());
        event.setFromMemberName(fromMember.getNickname());
        event.setToMemberName(toMember.getNickname());
        eventMapper.insert(event);
    }

    private Long currentUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }
}

