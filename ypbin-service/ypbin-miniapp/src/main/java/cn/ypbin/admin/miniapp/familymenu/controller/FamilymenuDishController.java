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

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuPresetDish;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuDishSaveReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuDishResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuDishService;
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
 * 家庭菜单-菜品库控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/familymenu")
@RequiredArgsConstructor
public class FamilymenuDishController {

    private final FamilymenuDishService dishService;

    /**
     * 餐厅菜品列表。
     */
    @GetMapping("/dish/list")
    public R<List<FamilymenuDishResp>> getDishList(
        @RequestParam("roomId") Long roomId,
        @RequestParam(value = "category", required = false) String category) {
        return R.ok(dishService.getDishList(roomId, category));
    }

    /**
     * 菜品详情。
     */
    @GetMapping("/dish/detail")
    public R<FamilymenuDishResp> getDishDetail(@RequestParam("id") Long id) {
        return R.ok(dishService.getDishDetail(id));
    }

    /**
     * 新增或编辑菜品。
     */
    @PostMapping("/dish/submit")
    public R<Void> saveDish(@Valid @RequestBody FamilymenuDishSaveReq req) {
        dishService.saveDish(req);
        return R.ok();
    }

    /**
     * 批量删除菜品。
     */
    @PostMapping("/dish/remove")
    public R<Void> removeDish(@RequestBody Map<String, Object> body) {
        Object idsObj = body.get("ids");
        if (idsObj instanceof List<?> list) {
            List<Long> ids = list.stream().map(i -> Long.valueOf(i.toString())).toList();
            dishService.removeDishes(ids);
        } else if (idsObj instanceof String str) {
            List<Long> ids = List.of(str.split(",")).stream().map(String::trim).map(Long::valueOf).toList();
            dishService.removeDishes(ids);
        }
        return R.ok();
    }

    /**
     * 预设菜品库列表（双映射兼容 kebab-case 与 camelCase）。
     */
    @GetMapping({"/preset-dish/list", "/presetDish/list"})
    public R<List<FamilymenuPresetDish>> getPresetDishList(
        @RequestParam(value = "category", required = false) String category) {
        return R.ok(dishService.getPresetDishList(category));
    }

    /**
     * 导入单道预设菜。
     */
    @PostMapping({"/preset-dish/import", "/presetDish/import"})
    public R<Void> importPresetDish(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        Long roomId = Long.valueOf(body.get("roomId").toString());
        dishService.importPresetDish(id, roomId);
        return R.ok();
    }

    /**
     * 批量导入预设菜。
     */
    @PostMapping({"/preset-dish/import-batch", "/presetDish/import-batch"})
    public R<Void> importPresetDishBatch(@RequestBody Map<String, Object> body) {
        Long roomId = Long.valueOf(body.get("roomId").toString());
        dishService.importPresetDishBatch(roomId);
        return R.ok();
    }
}
