/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.familymenu.service.impl;

import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuDish;
import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuMember;
import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuRoom;
import cn.ypbin.admin.modules.miniapp.familymenu.enums.FamilymenuMemberRoleEnum;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuDishMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuMemberMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuRoomMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.model.req.FamilymenuRoomCreateReq;
import cn.ypbin.admin.modules.miniapp.familymenu.model.resp.FamilymenuMemberResp;
import cn.ypbin.admin.modules.miniapp.familymenu.model.resp.FamilymenuMyRoomResp;
import cn.ypbin.admin.modules.miniapp.familymenu.model.resp.FamilymenuRoomDetailResp;
import cn.ypbin.admin.modules.miniapp.familymenu.service.FamilymenuRoomService;
import cn.ypbin.admin.modules.miniapp.familymenu.support.FamilymenuRoomAccessSupport;
import cn.ypbin.admin.modules.miniapp.common.ws.WsTicketService;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 家庭菜单-家庭餐厅服务实现。
 *
 * <p><b>访问控制</b>：{@code roomId}/{@code memberId} 均由客户端提供，餐厅级读写一律先经
 * {@link FamilymenuRoomAccessSupport} 校验调用者与该餐厅的关系（成员/主厨/创建者），否则等于开放 IDOR。
 * 唯一无需归属校验的是「按邀请码加入」——它的前置条件是持有邀请码，且只写入调用者自己的成员记录。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class FamilymenuRoomServiceImpl extends BaseServiceImpl<FamilymenuRoomMapper, FamilymenuRoom>
    implements FamilymenuRoomService {

    private static final Logger log = LoggerFactory.getLogger(FamilymenuRoomServiceImpl.class);

    /** 邀请码候选个数（一次性批量查重，避免循环内查库） */
    private static final int INVITE_CODE_CANDIDATE_COUNT = 10;

    /** 邀请码基数与跨度（6 位） */
    private static final int INVITE_CODE_BASE = 100000;
    private static final int INVITE_CODE_BOUND = 900000;

    private static final Random RANDOM = new SecureRandom();

    private final FamilymenuMemberMapper memberMapper;
    private final FamilymenuDishMapper dishMapper;
    private final SysUserService userService;
    private final FamilymenuRoomAccessSupport accessSupport;
    private final WsTicketService wsTicketService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilymenuRoomDetailResp createRoom(FamilymenuRoomCreateReq req) {
        Long userId = accessSupport.currentUserId();
        String inviteCode = generateInviteCode();

        FamilymenuRoom room = new FamilymenuRoom();
        room.setOwnerUserId(userId);
        room.setName(req.getName().trim());
        room.setInviteCode(inviteCode);
        save(room);

        // 创建者默认为主厨
        String nick = resolveUserName(userId);
        FamilymenuMember member = new FamilymenuMember();
        member.setRoomId(room.getId());
        member.setUserId(userId);
        member.setNickname(nick);
        member.setRole(FamilymenuMemberRoleEnum.CHEF.getCode());
        memberMapper.insert(member);

        return buildRoomDetail(room);
    }

    @Override
    public FamilymenuRoomDetailResp getRoomDetail(Long roomId) {
        // 餐厅级读取必须校验成员身份，否则可遍历 roomId 看他人家庭数据（IDOR）
        accessSupport.requireMember(roomId);
        return buildRoomDetail(accessSupport.requireRoom(roomId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilymenuRoomDetailResp joinRoom(String inviteCode) {
        Long userId = accessSupport.currentUserId();
        if (!StringUtils.hasText(inviteCode)) {
            throw new BusinessException("邀请码不能为空");
        }
        FamilymenuRoom room = getOne(new LambdaQueryWrapper<FamilymenuRoom>()
            .eq(FamilymenuRoom::getInviteCode, inviteCode.trim())
            .orderByDesc(FamilymenuRoom::getId), false);

        if (room == null) {
            throw new BusinessException("未找到该邀请码对应的家庭餐厅");
        }

        // 检查是否已是成员
        FamilymenuMember exist = memberMapper.selectOne(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, room.getId())
            .eq(FamilymenuMember::getUserId, userId));

        if (exist == null) {
            String nick = resolveUserName(userId);
            FamilymenuMember member = new FamilymenuMember();
            member.setRoomId(room.getId());
            member.setUserId(userId);
            member.setNickname(nick);
            member.setRole(FamilymenuMemberRoleEnum.DINER.getCode());
            memberMapper.insert(member);
        }

        return buildRoomDetail(room);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveRoom(Long roomId) {
        // 只需成员身份：非成员本就无记录可删，这里显式拒绝以保持语义一致
        FamilymenuMember member = accessSupport.requireMember(roomId);
        FamilymenuRoom room = accessSupport.requireRoom(roomId);
        if (room.getOwnerUserId().equals(member.getUserId())) {
            throw new BusinessException("您是创建者，如需关闭请解散餐厅");
        }
        memberMapper.deleteById(member.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disbandRoom(Long roomId) {
        Long userId = accessSupport.currentUserId();
        FamilymenuRoom room = accessSupport.requireRoom(roomId);
        if (!room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("只有创建者可以解散餐厅");
        }
        removeById(roomId);
        memberMapper.delete(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, roomId));
    }

    @Override
    public List<FamilymenuMemberResp> getMemberList(Long roomId) {
        // 成员名单属餐厅内部数据，先校验调用者是成员
        accessSupport.requireMember(roomId);
        List<FamilymenuMember> list = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, roomId)
            .orderByAsc(FamilymenuMember::getId));

        if (CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        return list.stream().map(this::toMemberResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(Long memberId, String role) {
        FamilymenuMember member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("成员不存在");
        }
        // 改角色属主厨专属：先按目标成员所属餐厅校验调用者身份（此前任何人可改任意成员角色）
        accessSupport.requireChef(member.getRoomId());

        FamilymenuMemberRoleEnum targetRole;
        try {
            targetRole = FamilymenuMemberRoleEnum.of(role);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("角色必须为主厨或食客");
        }
        member.setRole(targetRole.getCode());
        memberMapper.updateById(member);
    }

    @Override
    public List<FamilymenuRoomDetailResp> getMyRooms() {
        Long userId = accessSupport.currentUserId();
        List<FamilymenuMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getUserId, userId)
            .orderByDesc(FamilymenuMember::getCreateTime));

        if (CollectionUtils.isEmpty(members)) {
            return List.of();
        }

        List<Long> roomIds = members.stream().map(FamilymenuMember::getRoomId).toList();
        List<FamilymenuRoom> rooms = listByIds(roomIds);
        if (CollectionUtils.isEmpty(rooms)) {
            return List.of();
        }

        // 批量取各房间的成员与菜品数：原实现是 rooms.stream().map(getRoomDetail(...))，
        // 每间房都会再打 2 次库（N+1），而架构门禁识别不了流式中间操作里的 DB 调用，只能在此手动批量化。
        // 注意：出参仍保留 members（对外契约不变），只是改为一次 IN 查询后在内存分组。
        Map<Long, List<FamilymenuMemberResp>> memberMap = loadMembersByRoom(roomIds);
        Map<Long, Long> dishCountMap = countDishesByRoom(roomIds);
        return rooms.stream()
            .map(room -> toRoomDetailResp(room,
                memberMap.getOrDefault(room.getId(), List.of()),
                dishCountMap.getOrDefault(room.getId(), 0L)))
            .toList();
    }

    @Override
    public FamilymenuMyRoomResp getMyRoom(Long roomId) {
        // 指定餐厅：走完整归属校验后返回「餐厅 + 我的成员记录」
        if (roomId != null) {
            FamilymenuMember me = accessSupport.requireMember(roomId);
            return buildMyRoom(accessSupport.requireRoom(roomId), me);
        }
        // 未指定：取我加入的第一个餐厅（无餐厅是正常空态，room/member 均为 null）
        Long userId = accessSupport.currentUserId();
        List<FamilymenuMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getUserId, userId)
            .orderByDesc(FamilymenuMember::getCreateTime));
        FamilymenuMyRoomResp resp = new FamilymenuMyRoomResp();
        if (CollectionUtils.isEmpty(members)) {
            return resp;
        }
        FamilymenuMember first = members.getFirst();
        FamilymenuRoom room = getById(first.getRoomId());
        if (room == null) {
            // 成员记录指向的餐厅已被解散：属数据不一致，暴露出来而非静默返回空
            log.warn("成员记录指向的餐厅不存在，memberId={}，roomId={}", first.getId(), first.getRoomId());
            return resp;
        }
        return buildMyRoom(room, first);
    }

    /**
     * 组装「我的餐厅」出参。
     *
     * @param room   餐厅
     * @param member 我在该餐厅的成员记录
     * @return 包装结果
     */
    private FamilymenuMyRoomResp buildMyRoom(FamilymenuRoom room, FamilymenuMember member) {
        FamilymenuMyRoomResp resp = new FamilymenuMyRoomResp();
        resp.setRoom(buildRoomDetail(room));
        resp.setMember(toMemberResp(member));
        return resp;
    }

    @Override
    public String createWsTicket(Long roomId) {
        // 换票同样是餐厅级访问：非成员不得订阅他人餐厅的实时变更
        FamilymenuMember member = accessSupport.requireMember(roomId);
        return wsTicketService.issue(member.getUserId(), roomId);
    }

    /**
     * 组装餐厅详情（含成员列表与菜品数）。
     *
     * @param room 餐厅实体
     * @return 餐厅详情
     */
    private FamilymenuRoomDetailResp buildRoomDetail(FamilymenuRoom room) {
        FamilymenuRoomDetailResp resp = new FamilymenuRoomDetailResp();
        resp.setId(room.getId());
        resp.setName(room.getName());
        resp.setInviteCode(room.getInviteCode());
        resp.setOwnerUserId(room.getOwnerUserId());
        resp.setMembers(getMemberListInternal(room.getId()));

        Long dishCount = dishMapper.selectCount(new LambdaQueryWrapper<FamilymenuDish>()
            .eq(FamilymenuDish::getRoomId, room.getId()));
        resp.setDishCount(dishCount != null ? dishCount.intValue() : 0);
        return resp;
    }

    /**
     * 组装餐厅详情出参。
     *
     * @param room      餐厅实体
     * @param members   成员列表
     * @param dishCount 菜品数
     * @return 餐厅详情
     */
    private FamilymenuRoomDetailResp toRoomDetailResp(FamilymenuRoom room,
                                                      List<FamilymenuMemberResp> members,
                                                      long dishCount) {
        FamilymenuRoomDetailResp resp = new FamilymenuRoomDetailResp();
        resp.setId(room.getId());
        resp.setName(room.getName());
        resp.setInviteCode(room.getInviteCode());
        resp.setOwnerUserId(room.getOwnerUserId());
        resp.setDishCount((int) dishCount);
        resp.setMembers(members);
        return resp;
    }

    /** 一次性取多个餐厅的成员并按餐厅分组（单次 IN 查询，杜绝逐房间查库） */
    private Map<Long, List<FamilymenuMemberResp>> loadMembersByRoom(List<Long> roomIds) {
        if (CollectionUtils.isEmpty(roomIds)) {
            return Map.of();
        }
        List<FamilymenuMember> all = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
            .in(FamilymenuMember::getRoomId, roomIds)
            .orderByAsc(FamilymenuMember::getId));
        Map<Long, List<FamilymenuMemberResp>> grouped = new HashMap<>();
        for (FamilymenuMember m : all) {
            grouped.computeIfAbsent(m.getRoomId(), k -> new ArrayList<>()).add(toMemberResp(m));
        }
        return grouped;
    }

    /** 一次性统计多个餐厅的菜品数（单次 IN 查询，杜绝逐房间查库） */
    private Map<Long, Long> countDishesByRoom(List<Long> roomIds) {
        if (CollectionUtils.isEmpty(roomIds)) {
            return Map.of();
        }
        List<FamilymenuDish> dishes = dishMapper.selectList(new LambdaQueryWrapper<FamilymenuDish>()
            .select(FamilymenuDish::getRoomId)
            .in(FamilymenuDish::getRoomId, roomIds));
        Map<Long, Long> counter = new HashMap<>();
        for (FamilymenuDish dish : dishes) {
            counter.merge(dish.getRoomId(), 1L, Long::sum);
        }
        return counter;
    }

    /** 内部成员列表（调用方已完成归属校验，此处不再重复校验） */
    private List<FamilymenuMemberResp> getMemberListInternal(Long roomId) {
        List<FamilymenuMember> list = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, roomId)
            .orderByAsc(FamilymenuMember::getId));
        if (CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        return list.stream().map(this::toMemberResp).toList();
    }

    private FamilymenuMemberResp toMemberResp(FamilymenuMember m) {
        FamilymenuMemberResp r = new FamilymenuMemberResp();
        r.setId(m.getId());
        r.setRoomId(m.getRoomId());
        r.setUserId(m.getUserId());
        r.setNickname(m.getNickname());
        r.setAvatarUrl(m.getAvatarUrl());
        r.setRole(m.getRole());
        return r;
    }

    private String generateInviteCode() {
        // 一次性生成候选邀请码，单次 IN 查询批量排除正在使用中的邀请码，杜绝循环查 DB
        List<String> candidates = new ArrayList<>(INVITE_CODE_CANDIDATE_COUNT);
        for (int i = 0; i < INVITE_CODE_CANDIDATE_COUNT; i++) {
            candidates.add(String.valueOf(INVITE_CODE_BASE + RANDOM.nextInt(INVITE_CODE_BOUND)));
        }
        Set<String> usedCodes = list(new LambdaQueryWrapper<FamilymenuRoom>()
            .select(FamilymenuRoom::getInviteCode)
            .in(FamilymenuRoom::getInviteCode, candidates))
            .stream()
            .map(FamilymenuRoom::getInviteCode)
            .collect(Collectors.toSet());
        for (String c : candidates) {
            if (!usedCodes.contains(c)) {
                return c;
            }
        }
        return String.valueOf(INVITE_CODE_BASE + RANDOM.nextInt(INVITE_CODE_BOUND));
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
            // 展示名解析失败（system 不可达等）仍回退占位名，但必须留痕：静默吞掉会让
            // 「所有人都显示成占位名」这类故障没有任何线索
            log.warn("家庭成员显示名解析失败，回退占位名；userId={}", userId, e);
        }
        return "家庭成员" + (userId % 1000);
    }
}
