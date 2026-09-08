/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.service.impl;

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuDish;
import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuPresetDish;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuDishMapper;
import cn.ypbin.admin.miniapp.familymenu.mapper.FamilymenuPresetDishMapper;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuDishSaveReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuDishResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuDishService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 家庭菜单-菜品库服务实现。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class FamilymenuDishServiceImpl extends BaseServiceImpl<FamilymenuDishMapper, FamilymenuDish>
    implements FamilymenuDishService {

    private final FamilymenuPresetDishMapper presetDishMapper;

    @Override
    public List<FamilymenuDishResp> getDishList(Long roomId, String category) {
        if (roomId == null) {
            return Collections.emptyList();
        }
        List<FamilymenuDish> list = list(new LambdaQueryWrapper<FamilymenuDish>()
            .eq(FamilymenuDish::getRoomId, roomId)
            .eq(StringUtils.hasText(category) && !"全部".equals(category), FamilymenuDish::getCategory, category)
            .orderByDesc(FamilymenuDish::getCreateTime));

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toDishResp).toList();
    }

    @Override
    public FamilymenuDishResp getDishDetail(Long id) {
        FamilymenuDish dish = getById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        return toDishResp(dish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDish(FamilymenuDishSaveReq req) {
        FamilymenuDish dish;
        if (req.getId() != null) {
            dish = getById(req.getId());
            if (dish == null) {
                throw new BusinessException("菜品不存在");
            }
        } else {
            dish = new FamilymenuDish();
        }
        dish.setRoomId(req.getRoomId());
        dish.setName(req.getName().trim());
        dish.setCategory(req.getCategory());
        dish.setImageUrl(req.getImageUrl());
        dish.setTags(req.getTags());
        dish.setPrice(req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO);
        saveOrUpdate(dish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDishes(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        removeByIds(ids);
    }

    @Override
    public List<FamilymenuPresetDish> getPresetDishList(String category) {
        List<FamilymenuPresetDish> list = presetDishMapper.selectList(new LambdaQueryWrapper<FamilymenuPresetDish>()
            .eq(StringUtils.hasText(category) && !"全部".equals(category), FamilymenuPresetDish::getCategory, category)
            .orderByAsc(FamilymenuPresetDish::getId));

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importPresetDish(Long presetId, Long roomId) {
        FamilymenuPresetDish preset = presetDishMapper.selectById(presetId);
        if (preset == null) {
            throw new BusinessException("预设菜品不存在");
        }
        FamilymenuDish dish = new FamilymenuDish();
        dish.setRoomId(roomId);
        dish.setName(preset.getName());
        dish.setCategory(preset.getCategory());
        dish.setImageUrl(preset.getImageUrl());
        dish.setTags(preset.getTags());
        dish.setPrice(BigDecimal.ZERO);
        save(dish);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importPresetDishBatch(Long roomId) {
        List<FamilymenuPresetDish> presets = presetDishMapper.selectList(new LambdaQueryWrapper<FamilymenuPresetDish>()
            .orderByAsc(FamilymenuPresetDish::getId));

        if (CollectionUtils.isEmpty(presets)) {
            return;
        }

        List<FamilymenuDish> dishes = new ArrayList<>();
        for (FamilymenuPresetDish p : presets) {
            FamilymenuDish d = new FamilymenuDish();
            d.setRoomId(roomId);
            d.setName(p.getName());
            d.setCategory(p.getCategory());
            d.setImageUrl(p.getImageUrl());
            d.setTags(p.getTags());
            d.setPrice(BigDecimal.ZERO);
            dishes.add(d);
        }
        saveBatch(dishes);
    }

    private FamilymenuDishResp toDishResp(FamilymenuDish d) {
        FamilymenuDishResp resp = new FamilymenuDishResp();
        BeanUtils.copyProperties(d, resp);
        return resp;
    }
}

