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

import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabHomeSummaryResp;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabRoomService;
import cn.ypbin.starter.core.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 牌账清-首页控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/cardtab/home")
@RequiredArgsConstructor
public class CardtabHomeController {

    private final CardtabRoomService roomService;

    /**
     * 首页概览（进行中房间、最近历史、今日统计）。
     */
    @GetMapping("/summary")
    public R<CardtabHomeSummaryResp> getSummary() {
        return R.ok(roomService.getHomeSummary());
    }
}
