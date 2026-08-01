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

import cn.ypbin.admin.system.entity.SysDict;
import cn.ypbin.admin.system.model.query.DictQuery;
import cn.ypbin.admin.system.model.req.DictSaveReq;
import cn.ypbin.admin.system.model.resp.DictResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 字典类型服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysDictService extends BaseService<SysDict> {

    /**
     * 分页查询字典类型。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DictResp> pageDicts(DictQuery query);

    /**
     * 新增字典（编码查重）。
     *
     * @param req 请求
     */
    void createDict(DictSaveReq req);

    /**
     * 编辑字典（编码查重排除自身）。
     *
     * @param id  字典 ID
     * @param req 请求
     */
    void updateDict(Long id, DictSaveReq req);

    /**
     * 删除字典（同时删除其下字典项，并刷新字典缓存）。
     *
     * @param id 字典 ID
     */
    void deleteDict(Long id);
}
