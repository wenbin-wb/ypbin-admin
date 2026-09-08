/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.controller;

import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabSettlementResp;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabSettlementService;
import cn.ypbin.starter.core.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 牌账清-结算控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/cardtab/rooms/{roomId}")
@RequiredArgsConstructor
public class CardtabSettlementController {

    private final CardtabSettlementService settlementService;

    /**
     * 结算详情与排名计算。
     */
    @GetMapping("/settlement")
    public R<CardtabSettlementResp> getSettlement(@PathVariable Long roomId) {
        return R.ok(settlementService.getSettlement(roomId));
    }

    /**
     * 确认结算并锁定房间。
     */
    @PostMapping("/settle")
    public R<CardtabSettlementResp> saveSettlement(@PathVariable Long roomId) {
        return R.ok(settlementService.saveSettlement(roomId));
    }
}
