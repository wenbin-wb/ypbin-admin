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

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuMember;
import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuWishlist;
import cn.ypbin.admin.miniapp.familymenu.enums.FamilymenuWishStatusEnum;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuMemberMapper;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuWishlistMapper;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuWishlistReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuWishlistResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuWishlistService;
import cn.ypbin.admin.miniapp.familymenu.support.FamilymenuRoomAccessSupport;
import cn.ypbin.admin.miniapp.common.ws.RoomSocketRegistry;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * 家庭菜单-心愿单服务实现。
 *
 * <p><b>访问控制</b>：心愿单属餐厅内部数据。「查看/提交」要求是餐厅成员；
 * 「接单/婉拒」是主厨的职责，故要求主厨身份——此前任意登录用户可替他人家庭处理心愿单（越权）。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class FamilymenuWishlistServiceImpl
    extends BaseServiceImpl<FamilymenuWishlistMapper, FamilymenuWishlist>
    implements FamilymenuWishlistService {

    private final FamilymenuMemberMapper memberMapper;
    private final FamilymenuRoomAccessSupport accessSupport;
    private final RoomSocketRegistry roomSocketRegistry;

    @Override
    public List<FamilymenuWishlistResp> getWishList(Long roomId) {
        if (roomId == null) {
            return List.of();
        }
        accessSupport.requireMember(roomId);

        List<FamilymenuWishlist> list = list(new LambdaQueryWrapper<FamilymenuWishlist>()
            .eq(FamilymenuWishlist::getRoomId, roomId)
            .orderByDesc(FamilymenuWishlist::getCreateTime));

        if (CollectionUtils.isEmpty(list)) {
            return List.of();
        }

        // 批量查询成员昵称（防 N+1）
        List<Long> userIds = list.stream().map(FamilymenuWishlist::getUserId).distinct().toList();
        Map<Long, String> nameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(userIds)) {
            List<FamilymenuMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<FamilymenuMember>()
                    .eq(FamilymenuMember::getRoomId, roomId)
                    .in(FamilymenuMember::getUserId, userIds));
            for (FamilymenuMember m : members) {
                nameMap.put(m.getUserId(), m.getNickname());
            }
        }

        return list.stream().map(w -> {
            FamilymenuWishlistResp r = new FamilymenuWishlistResp();
            BeanUtils.copyProperties(w, r);
            r.setRequesterName(nameMap.getOrDefault(w.getUserId(), "家人"));
            return r;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitWish(FamilymenuWishlistReq req) {
        Long userId = accessSupport.currentUserId();
        accessSupport.requireMember(req.getRoomId());

        FamilymenuWishlist wish = new FamilymenuWishlist();
        wish.setRoomId(req.getRoomId());
        wish.setUserId(userId);
        wish.setDishName(req.getDishName().trim());
        wish.setRemark(req.getRemark());
        wish.setWishStatus(FamilymenuWishStatusEnum.PENDING.getCode());
        save(wish);
        broadcastRefresh(req.getRoomId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptWish(Long id) {
        FamilymenuWishlist wish = requireWish(id);
        // 接单是主厨职责：按心愿所属餐厅校验主厨身份
        accessSupport.requireChef(wish.getRoomId());
        wish.setWishStatus(FamilymenuWishStatusEnum.ACCEPTED.getCode());
        updateById(wish);
        broadcastRefresh(wish.getRoomId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectWish(Long id) {
        FamilymenuWishlist wish = requireWish(id);
        accessSupport.requireChef(wish.getRoomId());
        wish.setWishStatus(FamilymenuWishStatusEnum.REJECTED.getCode());
        updateById(wish);
        broadcastRefresh(wish.getRoomId());
    }

    /**
     * 广播餐厅变化信号（端侧据此重拉心愿单与今日菜单）。
     */
    private void broadcastRefresh(Long roomId) {
        if (roomId == null) {
            return;
        }
        roomSocketRegistry.broadcast(roomId, "{\"type\":\"refresh_order\"}");
    }

    /**
     * 取心愿并保证存在。
     *
     * @param id 心愿 ID
     * @return 心愿实体
     */
    private FamilymenuWishlist requireWish(Long id) {
        FamilymenuWishlist wish = getById(id);
        if (wish == null) {
            throw new BusinessException("心愿不存在");
        }
        return wish;
    }
}
