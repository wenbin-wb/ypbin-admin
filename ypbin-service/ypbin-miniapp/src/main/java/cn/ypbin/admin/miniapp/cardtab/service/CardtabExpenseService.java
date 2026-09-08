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

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabExpense;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabExpenseReq;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.Map;

/**
 * 牌账清-支出记录服务接口。
 *
 * @author wenbin
 * @since 2026-09-08
 */
public interface CardtabExpenseService extends BaseService<CardtabExpense> {

    /**
     * 记一笔支出。
     */
    Map<String, Object> createExpense(Long roomId, CardtabExpenseReq req);

    /**
     * 撤销一笔支出。
     */
    void revokeExpense(Long roomId, Long expenseId);
}

