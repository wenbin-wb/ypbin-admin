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
import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuOrder;
import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuOrderDetail;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuMemberMapper;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuOrderDetailMapper;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuOrderMapper;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderDetailReq;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderStatusReq;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderSubmitReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuOrderDetailResp;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuOrderResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuOrderService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.security.identity.IdentityContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 家庭菜单-就餐订单服务实现。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class FamilymenuOrderServiceImpl extends BaseServiceImpl<FamilymenuOrderMapper, FamilymenuOrder>
    implements FamilymenuOrderService {

    private final FamilymenuOrderDetailMapper detailMapper;
    private final FamilymenuMemberMapper memberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilymenuOrderResp submitOrder(FamilymenuOrderSubmitReq req) {
        LocalDate date = req.getOrderDate() != null ? req.getOrderDate() : LocalDate.now();
        String mealTime = StringUtils.hasText(req.getMealTime()) ? req.getMealTime() : "DINNER";

        // 查询当天该餐厅是否已有开餐订单
        FamilymenuOrder order = getOne(new LambdaQueryWrapper<FamilymenuOrder>()
            .eq(FamilymenuOrder::getRoomId, req.getRoomId())
            .eq(FamilymenuOrder::getOrderDate, date)
            .eq(FamilymenuOrder::getMealTime, mealTime)
            .orderByDesc(FamilymenuOrder::getId), false);

        if (order == null) {
            order = new FamilymenuOrder();
            order.setRoomId(req.getRoomId());
            order.setOrderDate(date);
            order.setMealTime(mealTime);
            order.setOrderStatus("PICKING");
            save(order);
        }

        return getOrderDetail(order.getId());
    }

    @Override
    public FamilymenuOrderResp getTodayOrder(Long roomId) {
        if (roomId == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        FamilymenuOrder order = getOne(new LambdaQueryWrapper<FamilymenuOrder>()
            .eq(FamilymenuOrder::getRoomId, roomId)
            .eq(FamilymenuOrder::getOrderDate, today)
            .orderByDesc(FamilymenuOrder::getId), false);

        if (order == null) {
            return null;
        }
        return getOrderDetail(order.getId());
    }

    @Override
    public FamilymenuOrderResp getOrderDetail(Long id) {
        FamilymenuOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        FamilymenuOrderResp resp = new FamilymenuOrderResp();
        BeanUtils.copyProperties(order, resp);
        resp.setDetails(getOrderDetails(order.getId()));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(FamilymenuOrderStatusReq req) {
        FamilymenuOrder order = getById(req.getId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        order.setOrderStatus(req.getOrderStatus());
        if (req.getCost() != null) {
            order.setCost(req.getCost());
        }
        if (StringUtils.hasText(req.getRemark())) {
            order.setRemark(req.getRemark().trim());
        }
        if (StringUtils.hasText(req.getPhoto())) {
            order.setPhoto(req.getPhoto().trim());
        }
        if (StringUtils.hasText(req.getCookingRemark())) {
            order.setCookingRemark(req.getCookingRemark().trim());
        }
        if (StringUtils.hasText(req.getCookingPhoto())) {
            order.setCookingPhoto(req.getCookingPhoto().trim());
        }
        updateById(order);
    }

    @Override
    public List<FamilymenuOrderResp> getOrderHistory(Long roomId) {
        if (roomId == null) {
            return Collections.emptyList();
        }
        List<FamilymenuOrder> orders = list(new LambdaQueryWrapper<FamilymenuOrder>()
            .eq(FamilymenuOrder::getRoomId, roomId)
            .orderByDesc(FamilymenuOrder::getOrderDate)
            .orderByDesc(FamilymenuOrder::getId));

        if (CollectionUtils.isEmpty(orders)) {
            return Collections.emptyList();
        }

        return orders.stream().map(o -> {
            FamilymenuOrderResp resp = new FamilymenuOrderResp();
            BeanUtils.copyProperties(o, resp);
            resp.setDetails(getOrderDetails(o.getId()));
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrderDetail(FamilymenuOrderDetailReq req) {
        Long userId = currentUserId();
        FamilymenuOrder order = getById(req.getOrderId());
        if (order == null) {
            throw new BusinessException("开餐订单不存在");
        }

        FamilymenuOrderDetail detail = new FamilymenuOrderDetail();
        detail.setOrderId(order.getId());
        detail.setDishId(req.getDishId());
        detail.setDishName(req.getDishName().trim());
        detail.setDishImage(req.getDishImage());
        detail.setQuantity(req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : 1);
        detail.setRequesterId(userId);
        detailMapper.insert(detail);
    }

    @Override
    public List<FamilymenuOrderDetailResp> getOrderDetails(Long orderId) {
        List<FamilymenuOrderDetail> list = detailMapper.selectList(new LambdaQueryWrapper<FamilymenuOrderDetail>()
            .eq(FamilymenuOrderDetail::getOrderId, orderId)
            .orderByAsc(FamilymenuOrderDetail::getId));

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        // 批量查询成员昵称映射（防止 N+1）
        List<Long> requesterIds = list.stream().map(FamilymenuOrderDetail::getRequesterId).distinct().toList();
        Map<Long, String> nameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(requesterIds)) {
            List<FamilymenuMember> members = memberMapper.selectList(new LambdaQueryWrapper<FamilymenuMember>()
                .in(FamilymenuMember::getUserId, requesterIds));
            for (FamilymenuMember m : members) {
                nameMap.put(m.getUserId(), m.getNickname());
            }
        }

        return list.stream().map(d -> {
            FamilymenuOrderDetailResp r = new FamilymenuOrderDetailResp();
            BeanUtils.copyProperties(d, r);
            r.setRequesterName(nameMap.getOrDefault(d.getRequesterId(), "家人"));
            return r;
        }).toList();
    }

    private Long currentUserId() {
        return IdentityContext.getUserId()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }
}

