/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysDictItem;
import cn.ypbin.admin.system.model.req.DictItemSaveReq;
import cn.ypbin.admin.system.model.resp.DictItemResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 字典项服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysDictItemService extends BaseService<SysDictItem> {

    /**
     * 查询指定字典下的字典项列表（按 sort 升序）。
     *
     * @param dictId 字典 ID
     * @return 字典项列表
     */
    List<DictItemResp> listByDictId(Long dictId);

    /**
     * 新增字典项（同字典内值查重，并刷新字典缓存）。
     *
     * @param req 请求
     */
    void createItem(DictItemSaveReq req);

    /**
     * 编辑字典项（同字典内值查重排除自身，并刷新字典缓存）。
     *
     * @param id  字典项 ID
     * @param req 请求
     */
    void updateItem(Long id, DictItemSaveReq req);

    /**
     * 删除字典项（并刷新字典缓存）。
     *
     * @param id 字典项 ID
     */
    void deleteItem(Long id);
}
