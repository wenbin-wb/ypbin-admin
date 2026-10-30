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

import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabExpense;
import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoomEvent;
import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoomMember;
import cn.ypbin.admin.modules.miniapp.cardtab.enums.CardtabEventTypeEnum;
import cn.ypbin.admin.modules.miniapp.cardtab.enums.CardtabMemberRoleEnum;
import cn.ypbin.admin.modules.miniapp.cardtab.enums.CardtabMemberStatusEnum;
import cn.ypbin.admin.modules.miniapp.cardtab.enums.CardtabRoomStatusEnum;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabExpenseMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomEventMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.admin.modules.miniapp.cardtab.model.req.CardtabRoomCreateReq;
import cn.ypbin.admin.modules.miniapp.cardtab.model.req.CardtabRoomJoinReq;
import cn.ypbin.admin.modules.miniapp.cardtab.model.req.CardtabRoomSettingsReq;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabEventResp;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabHomeSummaryResp;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabRoomDetailResp;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabRoomMemberResp;
import cn.ypbin.admin.modules.miniapp.cardtab.service.CardtabRoomService;
import cn.ypbin.admin.modules.miniapp.cardtab.support.CardtabRoomAccessSupport;
import cn.ypbin.admin.modules.miniapp.common.ws.RoomSocketRegistry;
import cn.ypbin.admin.modules.miniapp.common.ws.WsTicketService;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 牌账清-房间服务实现。
 *
 * <p><b>访问控制</b>：{@code roomId} 全部由客户端提供，房间级读写一律先过
 * {@link CardtabRoomAccessSupport} 校验调用者与该房间的关系（成员/房主），否则等于开放 IDOR。
 * 唯一例外是按口令查询房间（登录放行名单内的匿名入口），它只返回不含成员明细与流水的预览视图。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class CardtabRoomServiceImpl extends BaseServiceImpl<CardtabRoomMapper, CardtabRoom>
    implements CardtabRoomService {

    private static final Logger log = LoggerFactory.getLogger(CardtabRoomServiceImpl.class);

    /** 默认玩法类型 */
    private static final String DEFAULT_GAME_TYPE = "MAHJONG";

    /** 默认计分单位 */
    private static final String DEFAULT_UNIT = "积分";

    /** 默认记账模式 */
    private static final String DEFAULT_BOOKKEEPING_MODE = "SELF_PAY_TO_MEMBER";

    /** 口令加入方式标识 */
    private static final String JOIN_TYPE_PASSCODE = "PASSCODE";

    /** 首页展示的最近房间数量 */
    private static final int HOME_RECENT_ROOM_LIMIT = 5;

    /** 房间详情内嵌的流水条数 */
    private static final int ROOM_DETAIL_EVENT_LIMIT = 20;

    /** 口令码候选个数（一次性批量查重，避免循环内查库） */
    private static final int ROOM_CODE_CANDIDATE_COUNT = 10;

    /** 4 位口令码基数/跨度 */
    private static final int SHORT_CODE_BASE = 1000;
    private static final int SHORT_CODE_BOUND = 9000;

    /** 6 位口令码基数/跨度（候选全部冲突时的兜底） */
    private static final int LONG_CODE_BASE = 100000;
    private static final int LONG_CODE_BOUND = 900000;

    /** 占位昵称取模基数（userId % 该值），仅用于远端昵称不可用时兜底展示 */
    private static final int PLACEHOLDER_NICKNAME_MOD = 1000;

    private static final Random RANDOM = new SecureRandom();

    private final CardtabRoomMemberMapper memberMapper;
    private final CardtabRoomEventMapper eventMapper;
    private final CardtabExpenseMapper expenseMapper;
    private final SysUserService userService;
    private final CardtabRoomAccessSupport accessSupport;
    private final WsTicketService wsTicketService;
    private final RoomSocketRegistry roomSocketRegistry;

    @Override
    public CardtabHomeSummaryResp getHomeSummary() {
        Long userId = accessSupport.currentUserId();
        CardtabHomeSummaryResp summary = new CardtabHomeSummaryResp();

        // 1. 当前用户在各房间的成员记录（含历史房间，用于统计场次）
        List<CardtabRoomMember> myMembers = memberMapper.selectList(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getUserId, userId)
            .orderByDesc(CardtabRoomMember::getCreateTime));

        // 我的总积分：各房间成员记录上的累计积分之和
        BigDecimal myScore = BigDecimal.ZERO;
        for (CardtabRoomMember m : myMembers) {
            if (m.getTotalScore() != null) {
                myScore = myScore.add(m.getTotalScore());
            }
        }
        summary.setMyScore(myScore);
        summary.setRoomCount(myMembers.size());

        // 2. 进行中的房间 + 待结算数
        List<Long> myRoomIds = myMembers.stream().map(CardtabRoomMember::getRoomId).distinct().toList();
        List<CardtabRoom> myRooms = CollectionUtils.isEmpty(myRoomIds)
            ? List.of() : listByIds(myRoomIds);

        List<CardtabRoom> ongoingRooms = myRooms.stream()
            .filter(r -> CardtabRoomStatusEnum.ONGOING.is(r.getRoomStatus()))
            .toList();
        summary.setOngoingCount(ongoingRooms.size());

        // 3. 最近房间（按成员记录顺序取前 N 个，保持与「最近参与」一致）
        List<Long> recentIds = myMembers.stream()
            .map(CardtabRoomMember::getRoomId)
            .distinct()
            .limit(HOME_RECENT_ROOM_LIMIT)
            .toList();
        List<CardtabRoom> recentRooms = CollectionUtils.isEmpty(recentIds)
            ? List.of() : listByIds(recentIds);

        // 4. 一次性统计相关房间的支出笔数，避免在下面逐房间查库（N+1）
        List<Long> summaryRoomIds = new ArrayList<>(recentIds);
        ongoingRooms.stream().map(CardtabRoom::getId).filter(id -> !summaryRoomIds.contains(id))
            .forEach(summaryRoomIds::add);
        Map<Long, Integer> expenseCountMap = countExpensesByRoom(summaryRoomIds);

        // 进行中房间：取最近一个（myMembers 已按创建时间倒序）
        for (CardtabRoomMember m : myMembers) {
            Optional<CardtabRoom> hit = ongoingRooms.stream()
                .filter(r -> r.getId().equals(m.getRoomId()))
                .findFirst();
            if (hit.isPresent()) {
                summary.setCurrent(buildRoomDetail(hit.get(), true,
                    expenseCountMap.getOrDefault(hit.get().getId(), 0)));
                break;
            }
        }

        summary.setRecentRooms(recentRooms.stream()
            .map(r -> toSimpleRoomResp(r, expenseCountMap.getOrDefault(r.getId(), 0)))
            .toList());

        // 5. 今日记账笔数（我参与的房间里，今天新增且未撤销的支出）
        summary.setTodayExpenseCount(countTodayExpenses(myRoomIds));

        return summary;
    }

    /**
     * 统计「我参与的房间里今天新增的未撤销支出」笔数。
     *
     * @param roomIds 我参与的房间 ID
     * @return 笔数
     */
    private int countTodayExpenses(List<Long> roomIds) {
        if (CollectionUtils.isEmpty(roomIds)) {
            return 0;
        }
        // 单次 IN + 时间范围查询，不做循环查询；逻辑删除由 BaseEntity 的 @TableLogic 自动过滤
        return Math.toIntExact(expenseMapper.selectCount(new LambdaQueryWrapper<CardtabExpense>()
            .in(CardtabExpense::getRoomId, roomIds)
            .ge(CardtabExpense::getCreateTime, LocalDate.now().atStartOfDay())));
    }

    /**
     * 批量统计各房间的未撤销支出笔数（单次 IN 查询）。
     *
     * @param roomIds 房间 ID 集合
     * @return 房间 ID → 笔数
     */
    private Map<Long, Integer> countExpensesByRoom(List<Long> roomIds) {
        if (CollectionUtils.isEmpty(roomIds)) {
            return Map.of();
        }
        List<Map<String, Object>> rows = expenseMapper.countByRoomIds(roomIds);
        Map<Long, Integer> counter = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object roomId = row.get("roomId");
            Object count = row.get("expenseCount");
            if (roomId != null && count != null) {
                counter.put(((Number) roomId).longValue(), ((Number) count).intValue());
            }
        }
        return counter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CardtabRoomDetailResp createRoom(CardtabRoomCreateReq req) {
        Long userId = accessSupport.currentUserId();
        String roomCode = generateUniqueRoomCode();

        CardtabRoom room = new CardtabRoom();
        room.setOwnerUserId(userId);
        room.setName(req.getName());
        room.setRoomCode(roomCode);
        room.setGameType(StringUtils.hasText(req.getGameType()) ? req.getGameType() : DEFAULT_GAME_TYPE);
        room.setUnit(StringUtils.hasText(req.getUnit()) ? req.getUnit() : DEFAULT_UNIT);
        room.setBookkeepingMode(StringUtils.hasText(req.getBookkeepingMode())
            ? req.getBookkeepingMode() : DEFAULT_BOOKKEEPING_MODE);
        room.setAllowPasscodeJoin(Boolean.TRUE.equals(req.getAllowPasscodeJoin()) ? 1 : 0);
        room.setRoomStatus(CardtabRoomStatusEnum.ONGOING.getCode());
        save(room);

        // 创建者自动加入房间
        String myName = resolveUserName(userId);
        CardtabRoomMember member = new CardtabRoomMember();
        member.setRoomId(room.getId());
        member.setUserId(userId);
        member.setNickname(myName);
        member.setAvatarText(avatarText(myName));
        member.setRole(CardtabMemberRoleEnum.OWNER.getCode());
        member.setMemberStatus(CardtabMemberStatusEnum.IN_ROOM.getCode());
        member.setTotalScore(BigDecimal.ZERO);
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);

        // 记录创建流水
        recordEvent(room.getId(), CardtabEventTypeEnum.ROOM_CREATED, member.getId(), null, "创建房间",
            myName + " 创建了房间", BigDecimal.ZERO, myName, null);

        // 新房间必然没有支出，笔数为 0
        return buildRoomDetail(room, true, 0);
    }

    @Override
    public CardtabRoomDetailResp getRoomDetail(Long roomId) {
        // 房间级读取必须校验调用者是成员：否则任何登录用户都能遍历 roomId 看他人牌局（IDOR）
        accessSupport.requireMember(roomId);
        CardtabRoom room = accessSupport.requireRoom(roomId);
        return buildRoomDetail(room, true, countExpensesByRoom(List.of(roomId))
            .getOrDefault(roomId, 0));
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
        // 本接口在登录放行名单内（匿名可访问，供「凭口令入房」前预览），因此**不得**返回成员明细与流水：
        // 已登录且确属成员的调用者给全量视图，其余一律只给房间基本信息。
        Optional<Long> userId = accessSupport.currentUserIdOrEmpty();
        boolean isMember = userId
            .map(id -> {
                CardtabRoomMember member = accessSupport.findMember(room.getId(), id);
                return member != null && !CardtabMemberStatusEnum.LEFT.is(member.getMemberStatus());
            })
            .orElse(false);
        return buildRoomDetail(room, isMember, 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> joinRoom(CardtabRoomJoinReq req) {
        Long userId = accessSupport.currentUserId();
        CardtabRoom room = null;
        if (StringUtils.hasText(req.getRoomCode())) {
            room = getOne(new LambdaQueryWrapper<CardtabRoom>()
                .eq(CardtabRoom::getRoomCode, req.getRoomCode().trim())
                .orderByDesc(CardtabRoom::getId), false);
        }
        if (room == null) {
            throw new BusinessException("未找到房间，请检查口令码");
        }
        if (!CardtabRoomStatusEnum.ONGOING.is(room.getRoomStatus())) {
            throw new BusinessException("该房间已结算或关闭，无法加入");
        }
        if (JOIN_TYPE_PASSCODE.equalsIgnoreCase(req.getJoinType())
            && room.getAllowPasscodeJoin() != null && room.getAllowPasscodeJoin() == 0) {
            throw new BusinessException("房主已关闭口令加入功能");
        }

        // 检查是否已经是成员
        CardtabRoomMember existMember = accessSupport.findMember(room.getId(), userId);

        if (existMember != null) {
            if (CardtabMemberStatusEnum.LEFT.is(existMember.getMemberStatus())) {
                existMember.setMemberStatus(CardtabMemberStatusEnum.IN_ROOM.getCode());
                existMember.setJoinedAt(LocalDateTime.now());
                memberMapper.updateById(existMember);
                recordEvent(room.getId(), CardtabEventTypeEnum.MEMBER_JOINED, existMember.getId(), null,
                    "重新加入", existMember.getNickname() + " 重新进入了房间",
                    BigDecimal.ZERO, existMember.getNickname(), null);
            }
            Map<String, Object> res = new HashMap<>();
            res.put("roomId", room.getId());
            res.put("memberId", existMember.getId());
            res.put("joined", true);
            return res;
        }

        // 新增成员
        String nick = StringUtils.hasText(req.getNickname())
            ? req.getNickname().trim() : resolveUserName(userId);
        CardtabRoomMember member = new CardtabRoomMember();
        member.setRoomId(room.getId());
        member.setUserId(userId);
        member.setNickname(nick);
        member.setAvatarText(avatarText(nick));
        member.setRole(CardtabMemberRoleEnum.MEMBER.getCode());
        member.setMemberStatus(CardtabMemberStatusEnum.IN_ROOM.getCode());
        member.setTotalScore(BigDecimal.ZERO);
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);

        recordEvent(room.getId(), CardtabEventTypeEnum.MEMBER_JOINED, member.getId(), null, "新成员加入",
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
        Long userId = accessSupport.currentUserId();
        CardtabRoomMember member = accessSupport.findMember(roomId, userId);

        if (member == null) {
            return;
        }
        if (accessSupport.isOwnerRole(member.getRole())) {
            throw new BusinessException("房主不能退出房间，如需结束请直接结算");
        }
        member.setMemberStatus(CardtabMemberStatusEnum.LEFT.getCode());
        member.setLeftAt(LocalDateTime.now());
        memberMapper.updateById(member);

        recordEvent(roomId, CardtabEventTypeEnum.MEMBER_LEFT, member.getId(), null, "退出房间",
            member.getNickname() + " 退出了房间", BigDecimal.ZERO, member.getNickname(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoomSettings(Long roomId, CardtabRoomSettingsReq req) {
        // 房主专属：requireOwner 同时完成「房间存在」与「调用者是房主」两项校验
        CardtabRoom room = accessSupport.requireOwner(roomId);
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
        // 流水分页同样属房间级读取：先校验成员身份，避免跨房间窥探他人记账明细
        accessSupport.requireMember(roomId);
        Page<CardtabRoomEvent> pageParam = new Page<>(current, size);
        Page<CardtabRoomEvent> source = eventMapper.selectPage(pageParam,
            new LambdaQueryWrapper<CardtabRoomEvent>()
                .eq(CardtabRoomEvent::getRoomId, roomId)
                .orderByDesc(CardtabRoomEvent::getCreateTime));

        List<CardtabEventResp> list = source.getRecords().stream().map(this::toEventResp).toList();
        return PageResult.of(list, source.getTotal(), (int) source.getCurrent(), (int) source.getSize());
    }

    @Override
    public PageResult<CardtabRoomDetailResp> getHistoryRooms(int current, int size) {
        Long userId = accessSupport.currentUserId();
        Page<CardtabRoomMember> pageParam = new Page<>(current, size);
        Page<CardtabRoomMember> memberPage = memberMapper.selectPage(pageParam,
            new LambdaQueryWrapper<CardtabRoomMember>()
                .eq(CardtabRoomMember::getUserId, userId)
                .orderByDesc(CardtabRoomMember::getCreateTime));

        if (CollectionUtils.isEmpty(memberPage.getRecords())) {
            return PageResult.of(List.of(), 0, current, size);
        }

        List<Long> roomIds = memberPage.getRecords().stream().map(CardtabRoomMember::getRoomId).toList();
        List<CardtabRoom> rooms = listByIds(roomIds);
        // 批量取支出笔数，避免逐房间查库
        Map<Long, Integer> expenseCountMap = countExpensesByRoom(roomIds);
        List<CardtabRoomDetailResp> list = rooms.stream()
            .map(r -> toSimpleRoomResp(r, expenseCountMap.getOrDefault(r.getId(), 0)))
            .toList();
        return PageResult.of(list, memberPage.getTotal(), current, size);
    }

    @Override
    public String createWsTicket(Long roomId) {
        // 换票同样是房间级访问：非成员不得订阅他人房间的实时变更
        CardtabRoomMember member = accessSupport.requireMember(roomId);
        return wsTicketService.issue(member.getUserId(), roomId);
    }

    /**
     * 组装房间详情。
     *
     * @param room            房间实体
     * @param includeSensitive 是否包含成员明细与近期流水（匿名预览时为 false）
     * @param expenseCount    该房间的未撤销支出笔数
     * @return 房间详情
     */
    private CardtabRoomDetailResp buildRoomDetail(CardtabRoom room, boolean includeSensitive,
                                                  int expenseCount) {
        CardtabRoomDetailResp resp = toSimpleRoomResp(room, expenseCount);

        List<CardtabRoomMember> members = memberMapper.selectList(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getRoomId, room.getId())
            .orderByAsc(CardtabRoomMember::getId));
        resp.setMemberCount(members.size());

        // 我在本房间的积分：仅登录用户可见（匿名预览时为 0）
        Long currentUserId = accessSupport.currentUserIdOrEmpty().orElse(null);
        if (currentUserId != null) {
            members.stream()
                .filter(m -> currentUserId.equals(m.getUserId()))
                .findFirst()
                .ifPresent(m -> resp.setMyScore(m.getTotalScore() != null
                    ? m.getTotalScore() : BigDecimal.ZERO));
        }
        if (resp.getMyScore() == null) {
            resp.setMyScore(BigDecimal.ZERO);
        }

        if (!includeSensitive) {
            // 匿名/非成员的口令预览：人数可以给（便于「确认是这一桌」），但成员昵称/头像与
            // 流水（含金额）一律不外泄
            resp.setMembers(List.of());
            resp.setRecentEvents(List.of());
            return resp;
        }

        resp.setMembers(members.stream().map(this::toMemberResp).toList());

        Page<CardtabRoomEvent> eventPage = new Page<>(1, ROOM_DETAIL_EVENT_LIMIT);
        Page<CardtabRoomEvent> events = eventMapper.selectPage(eventPage,
            new LambdaQueryWrapper<CardtabRoomEvent>()
                .eq(CardtabRoomEvent::getRoomId, room.getId())
                .orderByDesc(CardtabRoomEvent::getCreateTime));

        resp.setRecentEvents(CollectionUtils.isEmpty(events.getRecords())
            ? List.of()
            : events.getRecords().stream().map(this::toEventResp).toList());

        return resp;
    }

    private String generateUniqueRoomCode() {
        // 一次性生成候选随机码，单次 IN 查询批量排除正在使用中的口令，杜绝循环内查 DB
        List<String> candidates = new ArrayList<>(ROOM_CODE_CANDIDATE_COUNT);
        for (int i = 0; i < ROOM_CODE_CANDIDATE_COUNT; i++) {
            candidates.add(String.valueOf(SHORT_CODE_BASE + RANDOM.nextInt(SHORT_CODE_BOUND)));
        }
        Set<String> usedCodes = list(new LambdaQueryWrapper<CardtabRoom>()
            .select(CardtabRoom::getRoomCode)
            .in(CardtabRoom::getRoomCode, candidates)
            .eq(CardtabRoom::getRoomStatus, CardtabRoomStatusEnum.ONGOING.getCode()))
            .stream()
            .map(CardtabRoom::getRoomCode)
            .collect(Collectors.toSet());

        for (String code : candidates) {
            if (!usedCodes.contains(code)) {
                return code;
            }
        }
        return String.valueOf(LONG_CODE_BASE + RANDOM.nextInt(LONG_CODE_BOUND));
    }

    private String resolveUserName(Long userId) {
        try {
            SysUser user = userService.getById(userId);
            if (user != null) {
                String n = user.getNickname();
                if (StringUtils.hasText(n)) {
                    return n;
                }
                if (StringUtils.hasText(user.getRealName())) {
                    return user.getRealName();
                }
            }
        } catch (Exception e) {
            // 回退占位名，但必须留痕：静默吞掉会让「所有人都显示成占位名」没有任何线索
            log.warn("玩家显示名解析失败，回退占位名；userId={}", userId, e);
        }
        return "玩家" + (userId % PLACEHOLDER_NICKNAME_MOD);
    }

    private String avatarText(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return "我";
        }
        String s = nickname.trim();
        return s.substring(s.length() - 1);
    }

    private void recordEvent(Long roomId, CardtabEventTypeEnum eventType, Long memberId, Long refId,
                             String title, String content, BigDecimal amount,
                             String fromName, String toName) {
        CardtabRoomEvent event = new CardtabRoomEvent();
        event.setRoomId(roomId);
        event.setEventType(eventType.getCode());
        event.setMemberId(memberId);
        event.setRefId(refId);
        event.setTitle(title);
        event.setContent(content);
        event.setAmount(amount != null ? amount : BigDecimal.ZERO);
        event.setFromMemberName(fromName);
        event.setToMemberName(toName);
        eventMapper.insert(event);
        // 流水即「房间有变化」的信号：广播给房间内实时连接，端侧收到后自行重拉详情
        broadcastRoomChanged(roomId);
    }

    /**
     * 广播「房间已变化」信号（端侧收到后重拉详情，服务端不推送业务数据本身）。
     *
     * <p>只推信号而非全量数据：避免在广播里重复做一次可能失败的查询，也让端侧以统一路径取数
     * （与轮询兜底走同一接口，行为一致）。</p>
     */
    private void broadcastRoomChanged(Long roomId) {
        roomSocketRegistry.broadcast(roomId, "{\"type\":\"ROOM_CHANGED\"}");
    }

    private CardtabRoomDetailResp toSimpleRoomResp(CardtabRoom r, int expenseCount) {
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
        resp.setExpenseCount(expenseCount);
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
        resp.setCreateTime(e.getCreateTime());
        return resp;
    }
}
