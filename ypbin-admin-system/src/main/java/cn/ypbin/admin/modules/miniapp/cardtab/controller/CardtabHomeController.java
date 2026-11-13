/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.controller;

import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabFrequentPlayerResp;
import cn.ypbin.admin.modules.miniapp.cardtab.model.resp.CardtabHomeSummaryResp;
import cn.ypbin.admin.modules.miniapp.cardtab.service.CardtabRoomService;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 牌账清-首页控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/miniapp/cardtab/home")
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

    /**
     * 常用玩家：按与我同房次数降序，供「我的」页展示。
     *
     * @param limit 返回条数上限（服务端会再夹到 1~20）
     */
    @GetMapping("/frequent-players")
    public R<List<CardtabFrequentPlayerResp>> frequentPlayers(@RequestParam(defaultValue = "4") int limit) {
        return R.ok(roomService.frequentPlayers(limit));
    }
}
