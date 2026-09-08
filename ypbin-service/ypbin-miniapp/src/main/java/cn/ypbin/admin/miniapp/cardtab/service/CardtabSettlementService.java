/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.service;

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabSettlementSnapshot;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabSettlementResp;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 牌账清-结算服务接口。
 *
 * @author wenbin
 * @since 2026-09-08
 */
public interface CardtabSettlementService extends BaseService<CardtabSettlementSnapshot> {

    /**
     * 计算并获取结算详情。
     */
    CardtabSettlementResp getSettlement(Long roomId);

    /**
     * 确认并保存结算快照。
     */
    CardtabSettlementResp saveSettlement(Long roomId);
}

