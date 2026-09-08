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
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomEventMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomCreateReq;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomJoinReq;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomSettingsReq;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabEventResp;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabHomeSummaryResp;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabRoomDetailResp;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabRoomMemberResp;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabRoomService;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.identity.IdentityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 牌账清-房间服务实现。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class CardtabRoomServiceImpl extends BaseServiceImpl<CardtabRoomMapper, CardtabRoom>
    implements CardtabRoomService {

    private final CardtabRoomMemberMapper memberMapper;
    private final CardtabRoomEventMapper eventMapper;
    private final ISystemClient systemClient;

    private static final Random RANDOM = new SecureRandom();

    @Override
    public CardtabHomeSummaryResp getHomeSummary() {
        Long userId = currentUserId();
        CardtabHomeSummaryResp summary = new CardtabHomeSummaryResp();

        // 1. 查询当前用户参与的所有房间成员记录
        List<CardtabRoomMember> myMembers = memberMapper.selectList(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getUserId, userId)
            .eq(CardtabRoomMember::getMemberStatus, "IN_ROOM")
            .orderByDesc(CardtabRoomMember::getCreateTime));

        if (!CollectionUtils.isEmpty(myMembers)) {
            List<Long> roomIds = myMembers.stream().map(CardtabRoomMember::getRoomId).toList();
            List<CardtabRoom> rooms = listByIds(roomIds);
            Map<Long, CardtabRoom> roomMap = new HashMap<>();
            for (CardtabRoom r : rooms) {
                roomMap.put(r.getId(), r);
            }

            // 寻找首个进行中的房间
            for (CardtabRoomMember m : myMembers) {
                CardtabRoom r = roomMap.get(m.getRoomId());
                if (r != null && "ONGOING".equals(r.getRoomStatus())) {
                    summary.setOngoingRoom(getRoomDetail(r.getId()));
                    break;
                }
            }

            // 统计待结算房间数
            long ongoingCount = rooms.stream().filter(r -> "ONGOING".equals(r.getRoomStatus())).count();
            summary.setUnsettledCount((int) ongoingCount);
        }

        // 2. 最近参与的历史房间（最多5个）
        Page<CardtabRoomMember> pageParam = new Page<>(1, 5);
        Page<CardtabRoomMember> histPage = memberMapper.selectPage(pageParam, new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getUserId, userId)
            .orderByDesc(CardtabRoomMember::getCreateTime));

        if (!CollectionUtils.isEmpty(histPage.getRecords())) {
            List<Long> recentIds = histPage.getRecords().stream().map(CardtabRoomMember::getRoomId).toList();
            List<CardtabRoom> recentRooms = listByIds(recentIds);
            summary.setRecentRooms(recentRooms.stream().map(this::toSimpleRoomResp).toList());
            summary.setMonthRoomCount((int) histPage.getTotal());
        } else {
            summary.setRecentRooms(Collections.emptyList());
        }

        // 计算今日积分变动
        BigDecimal todayTotal = BigDecimal.ZERO;
        for (CardtabRoomMember m : myMembers) {
            if (m.getTotalScore() != null) {
                todayTotal = todayTotal.add(m.getTotalScore());
            }
        }
        summary.setTodayScoreChange(todayTotal);

        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardtabRoomDetailResp createRoom(CardtabRoomCreateReq req) {
        Long userId = currentUserId();
        String roomCode = generateUniqueRoomCode();

        CardtabRoom room = new CardtabRoom();
        room.setOwnerUserId(userId);
        room.setName(req.getName());
        room.setRoomCode(roomCode);
        room.setGameType(StringUtils.hasText(req.getGameType()) ? req.getGameType() : "MAHJONG");
        room.setUnit(StringUtils.hasText(req.getUnit()) ? req.getUnit() : "积分");
        room.setBookkeepingMode(StringUtils.hasText(req.getBookkeepingMode()) ? req.getBookkeepingMode() : "SELF_PAY_TO_MEMBER");
        room.setAllowPasscodeJoin(Boolean.TRUE.equals(req.getAllowPasscodeJoin()) ? 1 : 0);
        room.setRoomStatus("ONGOING");
        save(room);

        // 创建者自动加入房间
        String myName = resolveUserName(userId);
        CardtabRoomMember member = new CardtabRoomMember();
        member.setRoomId(room.getId());
        member.setUserId(userId);
        member.setNickname(myName);
        member.setAvatarText(avatarText(myName));
        member.setRole("OWNER");
        member.setMemberStatus("IN_ROOM");
        member.setTotalScore(BigDecimal.ZERO);
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);

        // 记录创建流水
        recordEvent(room.getId(), "ROOM_CREATED", member.getId(), null, "创建房间",
            myName + " 创建了房间", BigDecimal.ZERO, myName, null);

        return getRoomDetail(room.getId());
    }

    @Override
    public CardtabRoomDetailResp getRoomDetail(Long roomId) {
        CardtabRoom room = getById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        CardtabRoomDetailResp resp = toSimpleRoomResp(room);

        // 查询成员
        List<CardtabRoomMember> members = memberMapper.selectList(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getRoomId, roomId)
            .orderByAsc(CardtabRoomMember::getId));

        resp.setMemberCount(members.size());
        resp.setMembers(members.stream().map(this::toMemberResp).toList());

        // 查询最近20条流水事件
        Page<CardtabRoomEvent> eventPage = new Page<>(1, 20);
        Page<CardtabRoomEvent> events = eventMapper.selectPage(eventPage, new LambdaQueryWrapper<CardtabRoomEvent>()
            .eq(CardtabRoomEvent::getRoomId, roomId)
            .orderByDesc(CardtabRoomEvent::getCreateTime));

        if (!CollectionUtils.isEmpty(events.getRecords())) {
            resp.setRecentEvents(events.getRecords().stream().map(this::toEventResp).toList());
        } else {
            resp.setRecentEvents(Collections.emptyList());
        }

        return resp;
    }

    @Override
    public CardtabRoomDetailResp getRoomByCode(String roomCode) {
        if (!StringUtils.hasText(roomCode)) {
            throw new BusinessException("房间口令码不能为空");
        }
        CardtabRoom room = getOne(new LambdaQueryWrapper<CardtabRoom>()
            .eq(CardtabRoom::getRoomCode, roomCode.trim())
            .orderByDesc(CardtabRoom::getId), false);

        if (room == null) {
            throw new BusinessException("未找到该口令对应的房间");
        }
        return getRoomDetail(room.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> joinRoom(CardtabRoomJoinReq req) {
        Long userId = currentUserId();
        CardtabRoom room = null;
        if (StringUtils.hasText(req.getRoomCode())) {
            room = getOne(new LambdaQueryWrapper<CardtabRoom>()
                .eq(CardtabRoom::getRoomCode, req.getRoomCode().trim())
                .orderByDesc(CardtabRoom::getId), false);
        }
        if (room == null) {
            throw new BusinessException("未找到房间，请检查口令码");
        }
        if (!"ONGOING".equals(room.getRoomStatus())) {
            throw new BusinessException("该房间已结算或关闭，无法加入");
        }
        if ("PASSCODE".equalsIgnoreCase(req.getJoinType()) && room.getAllowPasscodeJoin() != null && room.getAllowPasscodeJoin() == 0) {
            throw new BusinessException("房主已关闭口令加入功能");
        }

        // 检查是否已经是成员
        CardtabRoomMember existMember = memberMapper.selectOne(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getRoomId, room.getId())
            .eq(CardtabRoomMember::getUserId, userId));

        if (existMember != null) {
            if ("LEFT".equals(existMember.getMemberStatus())) {
                existMember.setMemberStatus("IN_ROOM");
                existMember.setJoinedAt(LocalDateTime.now());
                memberMapper.updateById(existMember);
                recordEvent(room.getId(), "MEMBER_JOINED", existMember.getId(), null, "重新加入",
                    existMember.getNickname() + " 重新进入了房间", BigDecimal.ZERO, existMember.getNickname(), null);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("roomId", room.getId());
            res.put("memberId", existMember.getId());
            res.put("joined", true);
            return res;
        }

        // 新增成员
        String nick = StringUtils.hasText(req.getNickname()) ? req.getNickname().trim() : resolveUserName(userId);
        CardtabRoomMember member = new CardtabRoomMember();
        member.setRoomId(room.getId());
        member.setUserId(userId);
        member.setNickname(nick);
        member.setAvatarText(avatarText(nick));
        member.setRole("MEMBER");
        member.setMemberStatus("IN_ROOM");
        member.setTotalScore(BigDecimal.ZERO);
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);

        recordEvent(room.getId(), "MEMBER_JOINED", member.getId(), null, "新成员加入",
            nick + " 加入了房间", BigDecimal.ZERO, nick, null);

        Map<String, Object> res = new HashMap<>();
        res.put("roomId", room.getId());
        res.put("memberId", member.getId());
        res.put("joined", true);
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveRoom(Long roomId) {
        Long userId = currentUserId();
        CardtabRoomMember member = memberMapper.selectOne(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getRoomId, roomId)
            .eq(CardtabRoomMember::getUserId, userId));

        if (member == null) {
            return;
        }
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException("房主不能退出房间，如需结束请直接结算");
        }
        member.setMemberStatus("LEFT");
        member.setLeftAt(LocalDateTime.now());
        memberMapper.updateById(member);

        recordEvent(roomId, "MEMBER_LEFT", member.getId(), null, "退出房间",
            member.getNickname() + " 退出了房间", BigDecimal.ZERO, member.getNickname(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoomSettings(Long roomId, CardtabRoomSettingsReq req) {
        Long userId = currentUserId();
        CardtabRoom room = getById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        if (!userId.equals(room.getOwnerUserId())) {
            throw new BusinessException("只有房主可以修改房间配置");
        }
        if (StringUtils.hasText(req.getName())) {
            room.setName(req.getName().trim());
        }
        if (StringUtils.hasText(req.getGameType())) {
            room.setGameType(req.getGameType());
        }
        if (StringUtils.hasText(req.getUnit())) {
            room.setUnit(req.getUnit());
        }
        if (StringUtils.hasText(req.getBookkeepingMode())) {
            room.setBookkeepingMode(req.getBookkeepingMode());
        }
        if (req.getAllowPasscodeJoin() != null) {
            room.setAllowPasscodeJoin(req.getAllowPasscodeJoin() ? 1 : 0);
        }
        updateById(room);
    }

    @Override
    public PageResult<CardtabEventResp> getRoomEvents(Long roomId, int current, int size) {
        Page<CardtabRoomEvent> pageParam = new Page<>(current, size);
        Page<CardtabRoomEvent> source = eventMapper.selectPage(pageParam, new LambdaQueryWrapper<CardtabRoomEvent>()
            .eq(CardtabRoomEvent::getRoomId, roomId)
            .orderByDesc(CardtabRoomEvent::getCreateTime));

        List<CardtabEventResp> list = source.getRecords().stream().map(this::toEventResp).toList();
        return PageResult.of(list, source.getTotal(), (int) source.getCurrent(), (int) source.getSize());
    }

    @Override
    public PageResult<CardtabRoomDetailResp> getHistoryRooms(int current, int size) {
        Long userId = currentUserId();
        Page<CardtabRoomMember> pageParam = new Page<>(current, size);
        Page<CardtabRoomMember> memberPage = memberMapper.selectPage(pageParam, new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getUserId, userId)
            .orderByDesc(CardtabRoomMember::getCreateTime));

        if (CollectionUtils.isEmpty(memberPage.getRecords())) {
            return PageResult.of(Collections.emptyList(), 0, current, size);
        }

        List<Long> roomIds = memberPage.getRecords().stream().map(CardtabRoomMember::getRoomId).toList();
        List<CardtabRoom> rooms = listByIds(roomIds);
        List<CardtabRoomDetailResp> list = rooms.stream().map(this::toSimpleRoomResp).toList();
        return PageResult.of(list, memberPage.getTotal(), current, size);
    }

    private Long currentUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    private String generateUniqueRoomCode() {
        for (int i = 0; i < 10; i++) {
            String code = String.valueOf(1000 + RANDOM.nextInt(9000));
            boolean exists = exists(new LambdaQueryWrapper<CardtabRoom>()
                .eq(CardtabRoom::getRoomCode, code)
                .eq(CardtabRoom::getRoomStatus, "ONGOING"));
            if (!exists) {
                return code;
            }
        }
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    private String resolveUserName(Long userId) {
        try {
            R<SysUser> r = systemClient.getUserById(userId);
            if (r != null && r.isSuccess() && r.getData() != null) {
                String n = r.getData().getNickname();
                if (StringUtils.hasText(n)) return n;
                if (StringUtils.hasText(r.getData().getRealName())) return r.getData().getRealName();
            }
        } catch (Exception ignored) {
        }
        return "玩家" + (userId % 1000);
    }

    private String avatarText(String nickname) {
        if (!StringUtils.hasText(nickname)) return "我";
        String s = nickname.trim();
        return s.substring(s.length() - 1);
    }

    private void recordEvent(Long roomId, String eventType, Long memberId, Long refId,
                             String title, String content, BigDecimal amount,
                             String fromName, String toName) {
        CardtabRoomEvent event = new CardtabRoomEvent();
        event.setRoomId(roomId);
        event.setEventType(eventType);
        event.setMemberId(memberId);
        event.setRefId(refId);
        event.setTitle(title);
        event.setContent(content);
        event.setAmount(amount != null ? amount : BigDecimal.ZERO);
        event.setFromMemberName(fromName);
        event.setToMemberName(toName);
        eventMapper.insert(event);
    }

    private CardtabRoomDetailResp toSimpleRoomResp(CardtabRoom r) {
        CardtabRoomDetailResp resp = new CardtabRoomDetailResp();
        resp.setId(r.getId());
        resp.setName(r.getName());
        resp.setRoomCode(r.getRoomCode());
        resp.setGameType(r.getGameType());
        resp.setUnit(r.getUnit());
        resp.setBookkeepingMode(r.getBookkeepingMode());
        resp.setAllowPasscodeJoin(r.getAllowPasscodeJoin() != null && r.getAllowPasscodeJoin() == 1);
        resp.setRoomStatus(r.getRoomStatus());
        resp.setOwnerUserId(r.getOwnerUserId());
        resp.setCreateTime(r.getCreateTime());
        return resp;
    }

    private CardtabRoomMemberResp toMemberResp(CardtabRoomMember m) {
        CardtabRoomMemberResp resp = new CardtabRoomMemberResp();
        resp.setId(m.getId());
        resp.setUserId(m.getUserId());
        resp.setNickname(m.getNickname());
        resp.setAvatarUrl(m.getAvatarUrl());
        resp.setAvatarText(m.getAvatarText());
        resp.setRole(m.getRole());
        resp.setMemberStatus(m.getMemberStatus());
        resp.setTotalScore(m.getTotalScore() != null ? m.getTotalScore() : BigDecimal.ZERO);
        return resp;
    }

    private CardtabEventResp toEventResp(CardtabRoomEvent e) {
        CardtabEventResp resp = new CardtabEventResp();
        resp.setId(e.getId());
        resp.setEventType(e.getEventType());
        resp.setTitle(e.getTitle());
        resp.setContent(e.getContent());
        resp.setAmount(e.getAmount());
        resp.setFromMemberName(e.getFromMemberName());
        resp.setToMemberName(e.getToMemberName());
        resp.setCreatedAt(e.getCreateTime());
        return resp;
    }
}

