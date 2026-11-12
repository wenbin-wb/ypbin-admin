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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabExpense;
import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoomEvent;
import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoomMember;
import cn.ypbin.admin.modules.miniapp.cardtab.enums.CardtabEventTypeEnum;
import cn.ypbin.admin.modules.miniapp.cardtab.enums.CardtabExpenseStatusEnum;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomEventMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.model.req.CardtabExpenseReq;
import cn.ypbin.admin.modules.miniapp.cardtab.support.CardtabRoomAccessSupport;
import cn.ypbin.admin.modules.miniapp.common.ws.RoomSocketRegistry;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 牌账清-记账与撤销的权限与积分回滚测试（不依赖数据库与 Spring 上下文）。
 *
 * <p>撤销会反向调整双方积分，等价于「改钱」：谁能撤销、撤销后积分如何回滚、
 * 多人可记模式下支出人如何解析，都必须有回归保护，否则一次放开入口就可能变成
 * 任何成员都能改他人账目。</p>
 *
 * <p>访问守卫使用<b>真实实现</b> + mock 的 Mapper，保证测的是真实越权判断而不是 mock 行为。</p>
 *
 * @author wenbin
 * @since 2026-11-13
 */
class CardtabExpenseServiceImplTest {

    private static final Long ROOM_ID = 100L;
    private static final Long EXPENSE_ID = 900L;
    /** 当前登录用户（调用者）的成员主键 */
    private static final long CALLER_MEMBER_ID = 7L;
    /** 房间内另一名成员 */
    private static final long OTHER_MEMBER_ID = 8L;

    private CardtabRoomMapper roomMapper;
    private CardtabRoomMemberMapper memberMapper;
    private CardtabRoomEventMapper eventMapper;
    private CardtabRoomAccessSupport accessSupport;
    private RoomSocketRegistry roomSocketRegistry;
    private CardtabExpenseServiceImpl service;

    @BeforeEach
    void setUp() {
        roomMapper = mock(CardtabRoomMapper.class);
        memberMapper = mock(CardtabRoomMemberMapper.class);
        eventMapper = mock(CardtabRoomEventMapper.class);
        roomSocketRegistry = mock(RoomSocketRegistry.class);
        accessSupport = new CardtabRoomAccessSupport(roomMapper, memberMapper);
        service = spy(new CardtabExpenseServiceImpl(memberMapper, eventMapper, accessSupport, roomSocketRegistry));
        doReturn(true).when(service).updateById(any(CardtabExpense.class));
        doReturn(true).when(service).save(any(CardtabExpense.class));
        loginAs(1L);
    }

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    private void loginAs(Long userId) {
        IdentityContext.setLoginUser(new LoginUser(userId, "u" + userId));
    }

    private CardtabRoom room(String status, String bookkeepingMode) {
        CardtabRoom room = new CardtabRoom();
        room.setId(ROOM_ID);
        room.setRoomStatus(status);
        room.setBookkeepingMode(bookkeepingMode);
        room.setOwnerUserId(1L);
        return room;
    }

    private CardtabRoomMember member(long id, long userId, String role, String score) {
        return member(id, userId, role, score, "IN_ROOM");
    }

    private CardtabRoomMember member(long id, long userId, String role, String score, String status) {
        CardtabRoomMember m = new CardtabRoomMember();
        m.setId(id);
        m.setRoomId(ROOM_ID);
        m.setUserId(userId);
        m.setRole(role);
        m.setNickname("成员" + id);
        m.setMemberStatus(status);
        m.setTotalScore(new BigDecimal(score));
        return m;
    }

    private CardtabExpense expense(long fromMemberId, long toMemberId, String status) {
        CardtabExpense expense = new CardtabExpense();
        expense.setId(EXPENSE_ID);
        expense.setRoomId(ROOM_ID);
        expense.setFromMemberId(fromMemberId);
        expense.setToMemberId(toMemberId);
        expense.setAmount(new BigDecimal("10"));
        expense.setExpenseStatus(status);
        return expense;
    }

    /** 当前登录用户是该房间成员（放行路径） */
    private void givenCallerIsMember(CardtabRoomMember caller) {
        when(memberMapper.selectOne(any())).thenReturn(caller);
    }

    @Test
    @DisplayName("记录人本人可以撤销自己记的支出，积分按原方向回滚并留下撤销流水")
    void recorderCanRevokeOwnExpense() {
        CardtabRoomMember caller = member(CALLER_MEMBER_ID, 1L, "MEMBER", "-10");
        givenCallerIsMember(caller);
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "SELF_PAY_TO_MEMBER"));
        CardtabExpense expense = expense(CALLER_MEMBER_ID, OTHER_MEMBER_ID,
            CardtabExpenseStatusEnum.NORMAL.getCode());
        expense.setCreateUser(1L);
        doReturn(expense).when(service).getById(EXPENSE_ID);
        CardtabRoomMember toMember = member(OTHER_MEMBER_ID, 2L, "MEMBER", "10");
        when(memberMapper.selectById(CALLER_MEMBER_ID)).thenReturn(caller);
        when(memberMapper.selectById(OTHER_MEMBER_ID)).thenReturn(toMember);

        service.revokeExpense(ROOM_ID, EXPENSE_ID);

        // 撤销：支出人加回金额，收款人扣回金额
        assertThat(caller.getTotalScore()).isEqualByComparingTo("0");
        assertThat(toMember.getTotalScore()).isEqualByComparingTo("0");
        assertThat(expense.getExpenseStatus()).isEqualTo(CardtabExpenseStatusEnum.REVOKED.getCode());
        verify(service).updateById(expense);
        verify(memberMapper, times(2)).updateById(any(CardtabRoomMember.class));
        ArgumentCaptor<CardtabRoomEvent> eventCaptor = ArgumentCaptor.forClass(CardtabRoomEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType())
            .isEqualTo(CardtabEventTypeEnum.EXPENSE_REVOKED.getCode());
        assertThat(eventCaptor.getValue().getRefId()).isEqualTo(EXPENSE_ID);
    }

    @Test
    @DisplayName("房主可以撤销其他成员记的支出")
    void ownerCanRevokeOthersExpense() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "OWNER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "SELF_PAY_TO_MEMBER"));
        CardtabExpense expense = expense(OTHER_MEMBER_ID, 9L, CardtabExpenseStatusEnum.NORMAL.getCode());
        doReturn(expense).when(service).getById(EXPENSE_ID);
        when(memberMapper.selectById(OTHER_MEMBER_ID)).thenReturn(member(OTHER_MEMBER_ID, 2L, "MEMBER", "10"));
        when(memberMapper.selectById(9L)).thenReturn(member(9L, 3L, "MEMBER", "-10"));

        service.revokeExpense(ROOM_ID, EXPENSE_ID);

        assertThat(expense.getExpenseStatus()).isEqualTo(CardtabExpenseStatusEnum.REVOKED.getCode());
        verify(eventMapper).insert(any(CardtabRoomEvent.class));
    }

    @Test
    @DisplayName("普通成员撤销别人的支出会被拒绝，且不改动任何数据")
    void plainMemberCannotRevokeOthersExpense() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "SELF_PAY_TO_MEMBER"));
        CardtabExpense expense = expense(OTHER_MEMBER_ID, 9L, CardtabExpenseStatusEnum.NORMAL.getCode());
        // 记录人是别人（createUser=2L），当前用户既非支出人也非记录人
        expense.setCreateUser(2L);
        doReturn(expense).when(service).getById(EXPENSE_ID);

        assertThatThrownBy(() -> service.revokeExpense(ROOM_ID, EXPENSE_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("只能撤销自己记的支出");

        verify(service, never()).updateById(any(CardtabExpense.class));
        verify(eventMapper, never()).insert(any(CardtabRoomEvent.class));
    }

    @Test
    @DisplayName("已结算房间不允许撤销")
    void settledRoomCannotRevoke() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "OWNER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("SETTLED", "SELF_PAY_TO_MEMBER"));

        assertThatThrownBy(() -> service.revokeExpense(ROOM_ID, EXPENSE_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("房间已结算或关闭");
    }

    @Test
    @DisplayName("已撤销的支出不能重复撤销")
    void revokedExpenseCannotBeRevokedAgain() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "SELF_PAY_TO_MEMBER"));
        doReturn(expense(CALLER_MEMBER_ID, OTHER_MEMBER_ID, CardtabExpenseStatusEnum.REVOKED.getCode()))
            .when(service).getById(EXPENSE_ID);

        assertThatThrownBy(() -> service.revokeExpense(ROOM_ID, EXPENSE_ID))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("该笔支出已撤销");
    }

    @Test
    @DisplayName("多人可记模式允许指定房间内其他成员作为支出人")
    void multiMemberModeAllowsOtherFromMember() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "MULTI_MEMBER_RECORD"));
        CardtabRoomMember other = member(OTHER_MEMBER_ID, 2L, "MEMBER", "0");
        when(memberMapper.selectById(OTHER_MEMBER_ID)).thenReturn(other);
        CardtabRoomMember toMember = member(9L, 3L, "MEMBER", "0");
        when(memberMapper.selectById(9L)).thenReturn(toMember);

        CardtabExpenseReq req = new CardtabExpenseReq();
        req.setFromMemberId(OTHER_MEMBER_ID);
        req.setToMemberId(9L);
        req.setAmount(new BigDecimal("5"));
        service.createExpense(ROOM_ID, req);

        ArgumentCaptor<CardtabExpense> expenseCaptor = ArgumentCaptor.forClass(CardtabExpense.class);
        verify(service).save(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getFromMemberId()).isEqualTo(OTHER_MEMBER_ID);
        assertThat(other.getTotalScore()).isEqualByComparingTo("-5");
        assertThat(toMember.getTotalScore()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("默认模式不允许代替他人记账，避免绕过「本人记账」语义")
    void selfModeRejectsOtherFromMember() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "SELF_PAY_TO_MEMBER"));

        CardtabExpenseReq req = new CardtabExpenseReq();
        req.setFromMemberId(OTHER_MEMBER_ID);
        req.setToMemberId(9L);
        req.setAmount(new BigDecimal("5"));

        assertThatThrownBy(() -> service.createExpense(ROOM_ID, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不允许代替他人记账");
        verify(service, never()).save(any(CardtabExpense.class));
    }

    @Test
    @DisplayName("多人可记模式下，代记账的记录人可以撤销自己代记的那笔")
    void multiModeRecorderCanRevokeExpenseOfOthers() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "MULTI_MEMBER_RECORD"));
        CardtabExpense expense = expense(OTHER_MEMBER_ID, 9L, CardtabExpenseStatusEnum.NORMAL.getCode());
        // 支出人是 B，记录人是当前用户（createUser=1L）
        expense.setCreateUser(1L);
        doReturn(expense).when(service).getById(EXPENSE_ID);
        when(memberMapper.selectById(OTHER_MEMBER_ID)).thenReturn(member(OTHER_MEMBER_ID, 2L, "MEMBER", "10"));
        when(memberMapper.selectById(9L)).thenReturn(member(9L, 3L, "MEMBER", "-10"));

        service.revokeExpense(ROOM_ID, EXPENSE_ID);

        assertThat(expense.getExpenseStatus()).isEqualTo(CardtabExpenseStatusEnum.REVOKED.getCode());
        verify(service).updateById(expense);
        verify(memberMapper, times(2)).updateById(any(CardtabRoomMember.class));
    }

    @Test
    @DisplayName("支出人本人即使不是记录人，也能撤销自己被代记的那笔")
    void payerCanRevokeExpenseRecordedByOthers() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "MULTI_MEMBER_RECORD"));
        CardtabExpense expense = expense(CALLER_MEMBER_ID, 9L, CardtabExpenseStatusEnum.NORMAL.getCode());
        // 记录人是另一个人（createUser=2L）
        expense.setCreateUser(2L);
        doReturn(expense).when(service).getById(EXPENSE_ID);
        when(memberMapper.selectById(CALLER_MEMBER_ID)).thenReturn(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(memberMapper.selectById(9L)).thenReturn(member(9L, 3L, "MEMBER", "0"));

        service.revokeExpense(ROOM_ID, EXPENSE_ID);

        assertThat(expense.getExpenseStatus()).isEqualTo(CardtabExpenseStatusEnum.REVOKED.getCode());
    }

    @Test
    @DisplayName("收款人已退出房间时不允许再记账，避免给幽灵成员加减分")
    void createExpenseRejectsLeftToMember() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "SELF_PAY_TO_MEMBER"));
        when(memberMapper.selectById(9L)).thenReturn(member(9L, 3L, "MEMBER", "0", "LEFT"));

        CardtabExpenseReq req = new CardtabExpenseReq();
        req.setToMemberId(9L);
        req.setAmount(new BigDecimal("5"));

        assertThatThrownBy(() -> service.createExpense(ROOM_ID, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("收款成员不存在或不在本房间内");
        verify(service, never()).save(any(CardtabExpense.class));
    }

    @Test
    @DisplayName("多人可记模式下，被指定的支出人已退出房间时拒绝记账")
    void multiModeRejectsLeftFromMember() {
        givenCallerIsMember(member(CALLER_MEMBER_ID, 1L, "MEMBER", "0"));
        when(roomMapper.selectByIdForUpdate(ROOM_ID)).thenReturn(room("ONGOING", "MULTI_MEMBER_RECORD"));
        when(memberMapper.selectById(OTHER_MEMBER_ID))
            .thenReturn(member(OTHER_MEMBER_ID, 2L, "MEMBER", "0", "LEFT"));

        CardtabExpenseReq req = new CardtabExpenseReq();
        req.setFromMemberId(OTHER_MEMBER_ID);
        req.setToMemberId(9L);
        req.setAmount(new BigDecimal("5"));

        assertThatThrownBy(() -> service.createExpense(ROOM_ID, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("支出人不存在或不在本房间内");
        verify(service, never()).save(any(CardtabExpense.class));
    }
}
