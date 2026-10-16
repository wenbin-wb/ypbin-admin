/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.support;

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoomMember;
import cn.ypbin.admin.miniapp.cardtab.enums.CardtabMemberRoleEnum;
import cn.ypbin.admin.miniapp.cardtab.enums.CardtabMemberStatusEnum;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMapper;
import cn.ypbin.admin.miniapp.cardtab.mapper.CardtabRoomMemberMapper;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.identity.IdentityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 牌账清-房间访问守卫。
 *
 * <p><b>存在理由（越权防护）</b>：房间类接口的入参 {@code roomId}/{@code memberId}/{@code expenseId}
 * 全部由客户端提供，若不校验「调用者与该房间的关系」，任何已登录用户都能靠遍历 ID 读取或篡改他人牌局
 * （典型 IDOR）。此前只有「记账」一处做了成员校验，房间详情、流水、结算等均可跨房间访问。</p>
 *
 * <p>约定：所有面向已登录用户的房间级接口，进入业务逻辑前必须先过本守卫；唯一的匿名入口
 * （按口令查房间，见网关白名单 {@code /miniapp/cardtab/rooms/code/**}）只允许返回不含成员明细与
 * 流水的预览视图，不得复用 {@code requireMember} 之后的全量查询。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
@Component
@RequiredArgsConstructor
public class CardtabRoomAccessSupport {

    private final CardtabRoomMapper roomMapper;
    private final CardtabRoomMemberMapper memberMapper;

    /**
     * 取当前登录用户 ID（缺失即抛，fail-closed）。
     *
     * @return 当前用户 ID
     */
    public Long currentUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    /**
     * 取当前登录用户 ID（匿名时为空，供白名单入口判断身份用）。
     *
     * @return 用户 ID 可选值
     */
    public Optional<Long> currentUserIdOrEmpty() {
        return IdentityContext.getUserId();
    }

    /**
     * 校验房间存在。
     *
     * @param roomId 房间 ID
     * @return 房间实体
     */
    public CardtabRoom requireRoom(Long roomId) {
        CardtabRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("房间不存在");
        }
        return room;
    }

    /**
     * 在当前用户视角下查找房间成员记录（缺省不含逻辑删除数据）。
     *
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @return 成员记录，非成员时为 null
     */
    public CardtabRoomMember findMember(Long roomId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<CardtabRoomMember>()
            .eq(CardtabRoomMember::getRoomId, roomId)
            .eq(CardtabRoomMember::getUserId, userId));
    }

    /**
     * 校验当前用户是该房间的在房成员。
     *
     * @param roomId 房间 ID
     * @return 当前用户的成员记录
     */
    public CardtabRoomMember requireMember(Long roomId) {
        Long userId = currentUserId();
        CardtabRoomMember member = findMember(roomId, userId);
        if (member == null || CardtabMemberStatusEnum.LEFT.is(member.getMemberStatus())) {
            // 不区分「房间不存在」与「你不是成员」：避免借报错差异探测他人房间是否存在
            throw new BusinessException("您不是该房间成员，无权访问");
        }
        return member;
    }

    /**
     * 校验当前用户是房间房主。
     *
     * @param roomId 房间 ID
     * @return 房间实体
     */
    public CardtabRoom requireOwner(Long roomId) {
        Long userId = currentUserId();
        CardtabRoom room = requireRoom(roomId);
        if (!userId.equals(room.getOwnerUserId())) {
            throw new BusinessException("只有房主可以执行该操作");
        }
        return room;
    }

    /**
     * 判断给定角色是否为房主。
     *
     * @param role 角色码
     * @return 是房主时为 true
     */
    public boolean isOwnerRole(String role) {
        return CardtabMemberRoleEnum.OWNER.is(role);
    }

    /**
     * 更新房间（供已过权限校验的调用方复用同一 Mapper，避免各处重复注入）。
     *
     * @param room 房间实体
     */
    public void updateRoom(CardtabRoom room) {
        roomMapper.updateById(room);
    }
}
