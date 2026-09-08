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

import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabExpenseReq;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabExpenseService;
import cn.ypbin.starter.core.model.R;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 牌账清-支出记录控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/cardtab/rooms/{roomId}/expenses")
@RequiredArgsConstructor
public class CardtabExpenseController {

    private final CardtabExpenseService expenseService;

    /**
     * 记一笔支出。
     */
    @PostMapping
    public R<Map<String, Object>> createExpense(
        @PathVariable Long roomId,
        @Valid @RequestBody CardtabExpenseReq req) {
        return R.ok(expenseService.createExpense(roomId, req));
    }

    /**
     * 撤销支出。
     */
    @DeleteMapping("/{expenseId}")
    public R<Void> revokeExpense(
        @PathVariable Long roomId,
        @PathVariable Long expenseId) {
        expenseService.revokeExpense(roomId, expenseId);
        return R.ok();
    }
}
