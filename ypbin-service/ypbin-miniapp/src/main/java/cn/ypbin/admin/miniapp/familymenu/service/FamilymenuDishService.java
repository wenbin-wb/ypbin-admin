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

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuDish;
import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuPresetDish;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuDishSaveReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuDishResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 家庭菜单-菜品库服务接口。
 *
 * @author wenbin
 * @since 2026-09-08
 */
public interface FamilymenuDishService extends BaseService<FamilymenuDish> {

    /**
     * 餐厅菜品列表。
     */
    List<FamilymenuDishResp> getDishList(Long roomId, String category);

    /**
     * 菜品详情。
     */
    FamilymenuDishResp getDishDetail(Long id);

    /**
     * 保存或更新菜品。
     */
    void saveDish(FamilymenuDishSaveReq req);

    /**
     * 批量删除菜品。
     */
    void removeDishes(List<Long> ids);

    /**
     * 系统预设菜品列表。
     */
    List<FamilymenuPresetDish> getPresetDishList(String category);

    /**
     * 导入单道预设菜品到餐厅。
     */
    void importPresetDish(Long presetId, Long roomId);

    /**
     * 一键批量导入全部常见预设菜品。
     */
    void importPresetDishBatch(Long roomId);
}

