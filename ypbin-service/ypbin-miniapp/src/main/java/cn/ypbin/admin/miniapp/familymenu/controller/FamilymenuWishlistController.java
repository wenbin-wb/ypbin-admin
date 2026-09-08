/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.controller;

import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuWishlistReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuWishlistResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuWishlistService;
import cn.ypbin.starter.core.model.R;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家庭菜单-心愿单控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/familymenu/wishlist")
@RequiredArgsConstructor
public class FamilymenuWishlistController {

    private final FamilymenuWishlistService wishlistService;

    /**
     * 餐厅心愿列表。
     */
    @GetMapping("/list")
    public R<List<FamilymenuWishlistResp>> getWishList(@RequestParam("roomId") Long roomId) {
        return R.ok(wishlistService.getWishList(roomId));
    }

    /**
     * 提交心愿。
     */
    @PostMapping("/submit")
    public R<Void> submitWish(@Valid @RequestBody FamilymenuWishlistReq req) {
        wishlistService.submitWish(req);
        return R.ok();
    }

    /**
     * 主厨接单。
     */
    @PostMapping("/accept")
    public R<Void> acceptWish(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        wishlistService.acceptWish(id);
        return R.ok();
    }

    /**
     * 主厨婉拒。
     */
    @PostMapping("/reject")
    public R<Void> rejectWish(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        wishlistService.rejectWish(id);
        return R.ok();
    }
}
