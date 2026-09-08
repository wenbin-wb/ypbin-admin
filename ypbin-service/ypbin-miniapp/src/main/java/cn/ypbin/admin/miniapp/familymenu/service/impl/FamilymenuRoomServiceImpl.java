/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.service.impl;

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuDish;
import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuMember;
import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuRoom;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuDishMapper;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuMemberMapper;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuRoomMapper;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuRoomCreateReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuMemberResp;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuRoomDetailResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuRoomService;
import cn.ypbin.admin.system.api.feign.ISystemClient;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.identity.IdentityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 家庭菜单-家庭餐厅服务实现。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class FamilymenuRoomServiceImpl extends BaseServiceImpl<FamilymenuRoomMapper, FamilymenuRoom>
    implements FamilymenuRoomService {

    private final FamilymenuMemberMapper memberMapper;
    private final FamilymenuDishMapper dishMapper;
    private final ISystemClient systemClient;

    private static final Random RANDOM = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilymenuRoomDetailResp createRoom(FamilymenuRoomCreateReq req) {
        Long userId = currentUserId();
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
        member.setRole("CHEF");
        memberMapper.insert(member);

        return getRoomDetail(room.getId());
    }

    @Override
    public FamilymenuRoomDetailResp getRoomDetail(Long roomId) {
        FamilymenuRoom room = getById(roomId);
        if (room == null) {
            throw new BusinessException("餐厅不存在");
        }
        FamilymenuRoomDetailResp resp = new FamilymenuRoomDetailResp();
        resp.setId(room.getId());
        resp.setName(room.getName());
        resp.setInviteCode(room.getInviteCode());
        resp.setOwnerUserId(room.getOwnerUserId());

        List<FamilymenuMemberResp> members = getMemberList(roomId);
        resp.setMembers(members);

        Long dishCount = dishMapper.selectCount(new LambdaQueryWrapper<FamilymenuDish>()
            .eq(FamilymenuDish::getRoomId, roomId));
        resp.setDishCount(dishCount != null ? dishCount.intValue() : 0);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilymenuRoomDetailResp joinRoom(String inviteCode) {
        Long userId = currentUserId();
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
            member.setRole("DINER");
            memberMapper.insert(member);
        }

        return getRoomDetail(room.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveRoom(Long roomId) {
        Long userId = currentUserId();
        FamilymenuRoom room = getById(roomId);
        if (room != null && room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("您是创建者，如需关闭请解散餐厅");
        }
        memberMapper.delete(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, roomId)
            .eq(FamilymenuMember::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disbandRoom(Long roomId) {
        Long userId = currentUserId();
        FamilymenuRoom room = getById(roomId);
        if (room == null) {
            throw new BusinessException("餐厅不存在");
        }
        if (!room.getOwnerUserId().equals(userId)) {
            throw new BusinessException("只有创建者可以解散餐厅");
        }
        removeById(roomId);
        memberMapper.delete(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, roomId));
    }

    @Override
    public List<FamilymenuMemberResp> getMemberList(Long roomId) {
        List<FamilymenuMember> list = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, roomId)
            .orderByAsc(FamilymenuMember::getId));

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(m -> {
            FamilymenuMemberResp r = new FamilymenuMemberResp();
            r.setId(m.getId());
            r.setRoomId(m.getRoomId());
            r.setUserId(m.getUserId());
            r.setNickname(m.getNickname());
            r.setAvatarUrl(m.getAvatarUrl());
            r.setRole(m.getRole());
            return r;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberRole(Long memberId, String role) {
        FamilymenuMember member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException("成员不存在");
        }
        if (!"CHEF".equalsIgnoreCase(role) && !"DINER".equalsIgnoreCase(role)) {
            throw new BusinessException("角色必须为主厨或食客");
        }
        member.setRole(role.toUpperCase());
        memberMapper.updateById(member);
    }

    @Override
    public List<FamilymenuRoomDetailResp> getMyRooms() {
        Long userId = currentUserId();
        List<FamilymenuMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getUserId, userId)
            .orderByDesc(FamilymenuMember::getCreateTime));

        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyList();
        }

        List<Long> roomIds = members.stream().map(FamilymenuMember::getRoomId).toList();
        List<FamilymenuRoom> rooms = listByIds(roomIds);
        if (CollectionUtils.isEmpty(rooms)) {
            return Collections.emptyList();
        }

        return rooms.stream().map(r -> getRoomDetail(r.getId())).toList();
    }

    @Override
    public FamilymenuRoomDetailResp getMyRoom(Long roomId) {
        if (roomId != null) {
            return getRoomDetail(roomId);
        }
        List<FamilymenuRoomDetailResp> myRooms = getMyRooms();
        if (CollectionUtils.isEmpty(myRooms)) {
            return null;
        }
        return myRooms.get(0);
    }

    private Long currentUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    private String generateInviteCode() {
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
        return "家庭成员" + (userId % 1000);
    }
}

