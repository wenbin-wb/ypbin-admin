/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.service;

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuOrder;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderDetailReq;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderStatusReq;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuOrderSubmitReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuOrderDetailResp;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuOrderResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 家庭菜单-就餐订单服务接口。
 *
 * @author wenbin
 * @since 2026-09-08
 */
public interface FamilymenuOrderService extends BaseService<FamilymenuOrder> {

    /**
     * 发起开餐订单。
     */
    FamilymenuOrderResp submitOrder(FamilymenuOrderSubmitReq req);

    /**
     * 获取今日订单（点餐状态）。
     */
    FamilymenuOrderResp getTodayOrder(Long roomId);

    /**
     * 订单详情（含明细）。
     */
    FamilymenuOrderResp getOrderDetail(Long id);

    /**
     * 更新订单状态（点餐中/等待做饭/烹饪中/已开饭）。
     */
    void updateOrderStatus(FamilymenuOrderStatusReq req);

    /**
     * 历史就餐记录。
     */
    List<FamilymenuOrderResp> getOrderHistory(Long roomId);

    /**
     * 点菜/添加订单明细。
     */
    void addOrderDetail(FamilymenuOrderDetailReq req);

    /**
     * 订单明细列表。
     */
    List<FamilymenuOrderDetailResp> getOrderDetails(Long orderId);
}

