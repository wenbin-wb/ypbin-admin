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

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuWishlist;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuWishlistReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuWishlistResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 家庭菜单-心愿单服务接口。
 *
 * @author wenbin
 * @since 2026-09-08
 */
public interface FamilymenuWishlistService extends BaseService<FamilymenuWishlist> {

    /**
     * 查询餐厅心愿列表。
     */
    List<FamilymenuWishlistResp> getWishList(Long roomId);

    /**
     * 提交心愿。
     */
    void submitWish(FamilymenuWishlistReq req);

    /**
     * 主厨接单。
     */
    void acceptWish(Long id);

    /**
     * 主厨婉拒。
     */
    void rejectWish(Long id);
}

