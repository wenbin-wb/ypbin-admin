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

import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuMember;
import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuOrder;
import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuOrderDetail;
import cn.ypbin.admin.modules.miniapp.familymenu.enums.FamilymenuMealTimeEnum;
import cn.ypbin.admin.modules.miniapp.familymenu.enums.FamilymenuOrderStatusEnum;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuMemberMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuOrderDetailMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuOrderMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.model.req.FamilymenuOrderDetailReq;
import cn.ypbin.admin.modules.miniapp.familymenu.model.req.FamilymenuOrderStatusReq;
import cn.ypbin.admin.modules.miniapp.familymenu.model.req.FamilymenuOrderSubmitReq;
import cn.ypbin.admin.modules.miniapp.familymenu.model.resp.FamilymenuOrderDetailResp;
import cn.ypbin.admin.modules.miniapp.familymenu.model.resp.FamilymenuOrderResp;
import cn.ypbin.admin.modules.miniapp.familymenu.service.FamilymenuOrderService;
import cn.ypbin.admin.modules.miniapp.familymenu.support.FamilymenuRoomAccessSupport;
import cn.ypbin.admin.modules.miniapp.common.ws.RoomSocketRegistry;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * <p><b>访问控制</b>：{@code roomId}/{@code orderId} 由客户端提供，订单级读写一律先经
 * {@link FamilymenuRoomAccessSupport} 校验调用者是该餐厅成员——此前任意登录用户可读他人
 * 就餐历史、改他人订单状态与采购费用（典型 IDOR）。</p>
 *
 * <p><b>性能</b>：历史记录与明细的「点菜人昵称」走批量 IN 查询组装，禁止在循环内查库。
 * 注意 {@code stream().map(...)} 里的 DB 调用逃得过架构门禁（它只识别 for/forEach），
 * 因此这里刻意用「先批量查、再内存拼装」的写法而非逐条查询。</p>
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
    private final FamilymenuRoomAccessSupport accessSupport;
    private final RoomSocketRegistry roomSocketRegistry;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilymenuOrderResp submitOrder(FamilymenuOrderSubmitReq req) {
        accessSupport.requireMember(req.getRoomId());

        LocalDate date = req.getOrderDate() != null ? req.getOrderDate() : LocalDate.now();
        String orderType = StringUtils.hasText(req.getOrderType())
            ? req.getOrderType() : FamilymenuMealTimeEnum.DINNER.getCode();

        // 查询当天该餐厅是否已有开餐订单
        FamilymenuOrder order = getOne(new LambdaQueryWrapper<FamilymenuOrder>()
            .eq(FamilymenuOrder::getRoomId, req.getRoomId())
            .eq(FamilymenuOrder::getOrderDate, date)
            .eq(FamilymenuOrder::getOrderType, orderType)
            .orderByDesc(FamilymenuOrder::getId), false);

        if (order == null) {
            order = new FamilymenuOrder();
            order.setRoomId(req.getRoomId());
            order.setOrderDate(date);
            order.setOrderType(orderType);
            order.setOrderStatus(FamilymenuOrderStatusEnum.ORDERING.getCode());
            save(order);
        }

        return getOrderDetailInternal(order);
    }

    @Override
    public FamilymenuOrderResp getTodayOrder(Long roomId) {
        if (roomId == null) {
            return null;
        }
        accessSupport.requireMember(roomId);

        LocalDate today = LocalDate.now();
        FamilymenuOrder order = getOne(new LambdaQueryWrapper<FamilymenuOrder>()
            .eq(FamilymenuOrder::getRoomId, roomId)
            .eq(FamilymenuOrder::getOrderDate, today)
            .orderByDesc(FamilymenuOrder::getId), false);

        if (order == null) {
            return null;
        }
        return getOrderDetailInternal(order);
    }

    @Override
    public FamilymenuOrderResp getOrderDetail(Long id) {
        FamilymenuOrder order = getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 必须用订单所属餐厅做归属校验：否则可按 orderId 遍历他人订单
        accessSupport.requireMember(order.getRoomId());
        return getOrderDetailInternal(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderStatus(FamilymenuOrderStatusReq req) {
        FamilymenuOrder order = getById(req.getId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 改状态/费用（采购金额）属写操作，先校验调用者属于该餐厅
        accessSupport.requireMember(order.getRoomId());

        if (!FamilymenuOrderStatusEnum.isValid(req.getOrderStatus())) {
            throw new BusinessException("非法的订单状态：" + req.getOrderStatus());
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
        // 订单状态/费用变更：广播让餐厅内所有端刷新进度
        broadcastOrderChanged(order.getRoomId());
    }

    @Override
    public List<FamilymenuOrderResp> getOrderHistory(Long roomId) {
        if (roomId == null) {
            return List.of();
        }
        accessSupport.requireMember(roomId);

        List<FamilymenuOrder> orders = list(new LambdaQueryWrapper<FamilymenuOrder>()
            .eq(FamilymenuOrder::getRoomId, roomId)
            .orderByDesc(FamilymenuOrder::getOrderDate)
            .orderByDesc(FamilymenuOrder::getId));

        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }

        // 批量取全部明细与点菜人昵称：原实现在 stream().map 里逐单查两次库（N+1），
        // 且该形态不被架构门禁识别，故必须在此手动批量化
        List<Long> orderIds = orders.stream().map(FamilymenuOrder::getId).toList();
        Map<Long, List<FamilymenuOrderDetailResp>> detailMap = loadDetailsByOrders(orderIds, roomId);

        return orders.stream().map(o -> {
            FamilymenuOrderResp resp = new FamilymenuOrderResp();
            BeanUtils.copyProperties(o, resp);
            resp.setDetails(detailMap.getOrDefault(o.getId(), List.of()));
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrderDetail(FamilymenuOrderDetailReq req) {
        Long userId = accessSupport.currentUserId();
        FamilymenuOrder order = getById(req.getOrderId());
        if (order == null) {
            throw new BusinessException("开餐订单不存在");
        }
        accessSupport.requireMember(order.getRoomId());

        FamilymenuOrderDetail detail = new FamilymenuOrderDetail();
        detail.setOrderId(order.getId());
        detail.setDishId(req.getDishId());
        detail.setDishName(req.getDishName().trim());
        detail.setDishImage(req.getDishImage());
        detail.setQuantity(req.getQuantity() != null && req.getQuantity() > 0 ? req.getQuantity() : 1);
        detail.setRequesterId(userId);
        detailMapper.insert(detail);
        // 新点菜明细：广播让主厨与其他家人看到
        broadcastOrderChanged(order.getRoomId());
    }

    /**
     * 广播「订单已变化」信号（端侧收到 {@code refresh_order} 后重拉）。
     *
     * <p>消息类型沿用端侧既有契约 {@code refresh_order}（见 familymenu-ui 的 WS 处理分支），
     * 不另造类型名，避免端侧要多认一套。</p>
     */
    private void broadcastOrderChanged(Long roomId) {
        if (roomId == null) {
            return;
        }
        roomSocketRegistry.broadcast(roomId, "{\"type\":\"refresh_order\"}");
    }

    @Override
    public List<FamilymenuOrderDetailResp> getOrderDetails(Long orderId) {
        FamilymenuOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        accessSupport.requireMember(order.getRoomId());
        return loadDetails(orderId, order.getRoomId());
    }

    /**
     * 组装订单详情（调用方已完成归属校验）。
     */
    private FamilymenuOrderResp getOrderDetailInternal(FamilymenuOrder order) {
        FamilymenuOrderResp resp = new FamilymenuOrderResp();
        BeanUtils.copyProperties(order, resp);
        resp.setDetails(loadDetails(order.getId(), order.getRoomId()));
        return resp;
    }

    /**
     * 取单个订单的明细（含点菜人昵称）。
     */
    private List<FamilymenuOrderDetailResp> loadDetails(Long orderId, Long roomId) {
        List<FamilymenuOrderDetail> list = detailMapper.selectList(
            new LambdaQueryWrapper<FamilymenuOrderDetail>()
                .eq(FamilymenuOrderDetail::getOrderId, orderId)
                .orderByAsc(FamilymenuOrderDetail::getId));
        return toDetailRespList(list, roomId);
    }

    /**
     * 批量取多个订单的明细（单次 IN 查询，避免逐单查库）。
     */
    private Map<Long, List<FamilymenuOrderDetailResp>> loadDetailsByOrders(List<Long> orderIds, Long roomId) {
        if (CollectionUtils.isEmpty(orderIds)) {
            return Map.of();
        }
        List<FamilymenuOrderDetail> all = detailMapper.selectList(
            new LambdaQueryWrapper<FamilymenuOrderDetail>()
                .in(FamilymenuOrderDetail::getOrderId, orderIds)
                .orderByAsc(FamilymenuOrderDetail::getId));

        Map<Long, List<FamilymenuOrderDetail>> grouped = new HashMap<>();
        for (FamilymenuOrderDetail d : all) {
            grouped.computeIfAbsent(d.getOrderId(), k -> new ArrayList<>()).add(d);
        }

        Map<Long, List<FamilymenuOrderDetailResp>> result = new HashMap<>();
        grouped.forEach((orderId, details) -> result.put(orderId, toDetailRespList(details, roomId)));
        return result;
    }

    /**
     * 明细 → 出参（统一在此批量补点菜人昵称，杜绝逐条查库）。
     *
     * @param list   订单明细
     * @param roomId 订单所属餐厅；昵称必须限定在该餐厅内，避免同一用户在多餐厅时串用他店昵称
     */
    private List<FamilymenuOrderDetailResp> toDetailRespList(List<FamilymenuOrderDetail> list, Long roomId) {
        if (CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        // 批量查询成员昵称映射（防 N+1）
        List<Long> requesterIds = list.stream()
            .map(FamilymenuOrderDetail::getRequesterId).distinct().toList();
        Map<Long, String> nameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(requesterIds)) {
            List<FamilymenuMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<FamilymenuMember>()
                    .eq(roomId != null, FamilymenuMember::getRoomId, roomId)
                    .in(FamilymenuMember::getUserId, requesterIds));
            for (FamilymenuMember m : members) {
                nameMap.putIfAbsent(m.getUserId(), m.getNickname());
            }
        }

        return list.stream().map(d -> {
            FamilymenuOrderDetailResp r = new FamilymenuOrderDetailResp();
            BeanUtils.copyProperties(d, r);
            r.setRequesterName(nameMap.getOrDefault(d.getRequesterId(), "家人"));
            return r;
        }).toList();
    }
}
