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
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuMemberMapper;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuWishlistMapper;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuWishlistReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuWishlistResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuWishlistService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.identity.IdentityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Collections;
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
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class FamilymenuWishlistServiceImpl extends BaseServiceImpl<FamilymenuWishlistMapper, FamilymenuWishlist>
    implements FamilymenuWishlistService {

    private final FamilymenuMemberMapper memberMapper;

    @Override
    public List<FamilymenuWishlistResp> getWishList(Long roomId) {
        if (roomId == null) {
            return Collections.emptyList();
        }
        List<FamilymenuWishlist> list = list(new LambdaQueryWrapper<FamilymenuWishlist>()
            .eq(FamilymenuWishlist::getRoomId, roomId)
            .orderByDesc(FamilymenuWishlist::getCreateTime));

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        // 批量查询成员昵称（防 N+1）
        List<Long> userIds = list.stream().map(FamilymenuWishlist::getUserId).distinct().toList();
        Map<Long, String> nameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(userIds)) {
            List<FamilymenuMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
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
        Long userId = currentUserId();
        FamilymenuWishlist wish = new FamilymenuWishlist();
        wish.setRoomId(req.getRoomId());
        wish.setUserId(userId);
        wish.setDishName(req.getDishName().trim());
        wish.setRemark(req.getRemark());
        wish.setWishStatus("PENDING");
        save(wish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptWish(Long id) {
        FamilymenuWishlist wish = getById(id);
        if (wish == null) {
            throw new BusinessException("心愿不存在");
        }
        wish.setWishStatus("ACCEPTED");
        updateById(wish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectWish(Long id) {
        FamilymenuWishlist wish = getById(id);
        if (wish == null) {
            throw new BusinessException("心愿不存在");
        }
        wish.setWishStatus("REJECTED");
        updateById(wish);
    }

    private Long currentUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }
}

