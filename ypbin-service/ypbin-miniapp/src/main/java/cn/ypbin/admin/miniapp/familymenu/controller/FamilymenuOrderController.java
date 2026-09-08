/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.controller;

import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderDetailReq;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderStatusReq;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderSubmitReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuOrderDetailResp;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuOrderResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuOrderService;
import cn.ypbin.starter.core.model.R;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家庭菜单-就餐订单与点菜控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/familymenu")
@RequiredArgsConstructor
public class FamilymenuOrderController {

    private final FamilymenuOrderService orderService;

    /**
     * 发起开餐订单。
     */
    @PostMapping("/order/submit")
    public R<FamilymenuOrderResp> submitOrder(@Valid @RequestBody FamilymenuOrderSubmitReq req) {
        return R.ok(orderService.submitOrder(req));
    }

    /**
     * 获取今日就餐状态。
     */
    @GetMapping("/order/today")
    public R<FamilymenuOrderResp> getTodayOrder(@RequestParam("roomId") Long roomId) {
        return R.ok(orderService.getTodayOrder(roomId));
    }

    /**
     * 订单详情。
     */
    @GetMapping("/order/detail")
    public R<FamilymenuOrderResp> getOrderDetail(@RequestParam("id") Long id) {
        return R.ok(orderService.getOrderDetail(id));
    }

    /**
     * 更新订单状态。
     */
    @PostMapping("/order/status")
    public R<Void> updateOrderStatus(@Valid @RequestBody FamilymenuOrderStatusReq req) {
        orderService.updateOrderStatus(req);
        return R.ok();
    }

    /**
     * 历史就餐记录。
     */
    @GetMapping("/order/history")
    public R<List<FamilymenuOrderResp>> getOrderHistory(@RequestParam("roomId") Long roomId) {
        return R.ok(orderService.getOrderHistory(roomId));
    }

    /**
     * 点菜/添加菜品明细（双映射兼容）。
     */
    @PostMapping({"/order-detail/submit", "/orderDetail/submit"})
    public R<Void> addOrderDetail(@Valid @RequestBody FamilymenuOrderDetailReq req) {
        orderService.addOrderDetail(req);
        return R.ok();
    }

    /**
     * 订单明细列表（双映射兼容）。
     */
    @GetMapping({"/order-detail/list", "/orderDetail/list"})
    public R<List<FamilymenuOrderDetailResp>> getOrderDetails(@RequestParam("orderId") Long orderId) {
        return R.ok(orderService.getOrderDetails(orderId));
    }
}
