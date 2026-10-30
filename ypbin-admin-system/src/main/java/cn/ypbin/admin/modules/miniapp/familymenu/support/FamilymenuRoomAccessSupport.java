/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.familymenu.support;

import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuMember;
import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuRoom;
import cn.ypbin.admin.modules.miniapp.familymenu.enums.FamilymenuMemberRoleEnum;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuMemberMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuRoomMapper;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 家庭菜单-餐厅访问守卫。
 *
 * <p><b>存在理由（越权防护）</b>：{@code roomId}/{@code orderId}/{@code dishId}/{@code memberId}
 * 均由客户端提供，若不校验「调用者与该餐厅的关系」，任何已登录用户都能靠遍历 ID 读写他人家庭数据
 * （典型 IDOR）。此前除解散餐厅外几乎无归属校验。</p>
 *
 * <p>约定：餐厅级接口进入业务逻辑前先过本守卫；{@code /familymenu/preset-dish/list} 是全局预设菜
 * 字典（不隶属任何餐厅），在白名单中且无需归属校验。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
@Component
@RequiredArgsConstructor
public class FamilymenuRoomAccessSupport {

    private final FamilymenuRoomMapper roomMapper;
    private final FamilymenuMemberMapper memberMapper;

    /**
     * 取当前登录用户 ID（缺失即抛，fail-closed）。
     *
     * @return 当前用户 ID
     */
    public Long currentUserId() {
        return UserContext.getUserIdSafely()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    /**
     * 校验餐厅存在。
     *
     * @param roomId 餐厅 ID
     * @return 餐厅实体
     */
    public FamilymenuRoom requireRoom(Long roomId) {
        FamilymenuRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException("餐厅不存在");
        }
        return room;
    }

    /**
     * 校验当前用户是该餐厅成员。
     *
     * @param roomId 餐厅 ID
     * @return 当前用户的成员记录
     */
    public FamilymenuMember requireMember(Long roomId) {
        Long userId = currentUserId();
        FamilymenuMember member = memberMapper.selectOne(new LambdaQueryWrapper<FamilymenuMember>()
            .eq(FamilymenuMember::getRoomId, roomId)
            .eq(FamilymenuMember::getUserId, userId));
        if (member == null) {
            // 不区分「餐厅不存在」与「你不是成员」：避免借报错差异探测他人餐厅是否存在
            throw new BusinessException("您不是该餐厅成员，无权访问");
        }
        return member;
    }

    /**
     * 校验当前用户是该餐厅主厨（主厨专属操作：改成员角色、处理心愿单）。
     *
     * @param roomId 餐厅 ID
     * @return 当前用户的成员记录
     */
    public FamilymenuMember requireChef(Long roomId) {
        FamilymenuMember member = requireMember(roomId);
        if (!FamilymenuMemberRoleEnum.CHEF.getCode().equals(member.getRole())) {
            throw new BusinessException("只有主厨可以执行该操作");
        }
        return member;
    }

    /**
     * 校验当前用户是这些餐厅**全部**的成员（批量语义，用于批量删除这类跨多餐厅的入参）。
     *
     * <p>用一次 IN 查询完成判定，避免在调用方循环里逐个查库（那会形成 N+1）。</p>
     *
     * @param roomIds 餐厅 ID 集合
     */
    public void requireMemberOfAll(Collection<Long> roomIds) {
        if (CollectionUtils.isEmpty(roomIds)) {
            return;
        }
        Long userId = currentUserId();
        List<Long> distinctIds = roomIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            return;
        }
        List<FamilymenuMember> members = memberMapper.selectList(
            new LambdaQueryWrapper<FamilymenuMember>()
                .eq(FamilymenuMember::getUserId, userId)
                .in(FamilymenuMember::getRoomId, distinctIds));
        if (members.size() != distinctIds.size()) {
            // 不指明具体是哪个餐厅：避免借报错差异探测他人餐厅是否存在
            throw new BusinessException("存在不属于您的餐厅数据，无权操作");
        }
    }
}
