/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.familymenu.service.impl;

import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuDish;
import cn.ypbin.admin.modules.miniapp.familymenu.entity.FamilymenuPresetDish;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuDishMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.mapper.FamilymenuPresetDishMapper;
import cn.ypbin.admin.modules.miniapp.familymenu.model.req.FamilymenuDishSaveReq;
import cn.ypbin.admin.modules.miniapp.familymenu.model.resp.FamilymenuDishResp;
import cn.ypbin.admin.modules.miniapp.familymenu.model.resp.FamilymenuPresetDishResp;
import cn.ypbin.admin.modules.miniapp.familymenu.service.FamilymenuDishService;
import cn.ypbin.admin.modules.miniapp.familymenu.support.FamilymenuRoomAccessSupport;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * <p><b>访问控制</b>：菜品库隶属于餐厅（{@code familymenu_dish.room_id}），
 * 读写一律先经 {@link FamilymenuRoomAccessSupport} 校验调用者是该餐厅成员——此前任意登录用户
 * 可按 {@code roomId} 读写他人菜品库、按 {@code dishId} 取任意菜品详情（典型 IDOR）。
 * 全局预设菜（{@code familymenu_preset_dish}）不隶属任何餐厅，属公开字典，无需归属校验。</p>
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class FamilymenuDishServiceImpl extends BaseServiceImpl<FamilymenuDishMapper, FamilymenuDish>
    implements FamilymenuDishService {

    private final FamilymenuPresetDishMapper presetDishMapper;
    private final FamilymenuRoomAccessSupport accessSupport;

    @Override
    public List<FamilymenuDishResp> getDishList(Long roomId, String category) {
        if (roomId == null) {
            return List.of();
        }
        accessSupport.requireMember(roomId);

        List<FamilymenuDish> list = list(new LambdaQueryWrapper<FamilymenuDish>()
            .eq(FamilymenuDish::getRoomId, roomId)
            .eq(StringUtils.hasText(category) && !"全部".equals(category),
                FamilymenuDish::getCategory, category)
            .orderByDesc(FamilymenuDish::getCreateTime));

        if (CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        return list.stream().map(this::toDishResp).toList();
    }

    @Override
    public FamilymenuDishResp getDishDetail(Long id) {
        FamilymenuDish dish = getById(id);
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }
        // 菜品归属某餐厅，按 dishId 直读必须校验调用者属于该餐厅
        accessSupport.requireMember(dish.getRoomId());
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
            // 编辑既有菜品：按菜品原属餐厅校验，防止把他人菜品改到自己餐厅（也防越权改价）
            accessSupport.requireMember(dish.getRoomId());
            if (!dish.getRoomId().equals(req.getRoomId())) {
                throw new BusinessException("不允许把菜品迁移到其他餐厅");
            }
        } else {
            // 新增：校验目标餐厅归属
            accessSupport.requireMember(req.getRoomId());
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
        // 逐条校验归属（数量为客户端传入的批量删除，必须先确认每一道菜都属于自己餐厅）
        List<FamilymenuDish> dishes = listByIds(ids);
        if (dishes.size() != ids.size()) {
            // 有 ID 不存在（或已被删除）：批量删除语义下直接拒绝，避免「删了别人的却静默成功一半」
            throw new BusinessException("部分菜品不存在，请刷新后重试");
        }
        // 批量删除的 ids 来自客户端，可能跨多个餐厅：一次 IN 查询校验调用者是全部相关餐厅的成员
        // （逐条 requireMember 会形成循环内查库，架构门禁拦得住但没必要写出来）
        List<Long> roomIds = dishes.stream().map(FamilymenuDish::getRoomId).distinct().toList();
        accessSupport.requireMemberOfAll(roomIds);
        removeByIds(ids);
    }

    @Override
    public List<FamilymenuPresetDishResp> getPresetDishList(String category) {
        // 全局预设菜字典：不隶属任何餐厅，登录用户均可浏览（登录放行名单亦放行）
        List<FamilymenuPresetDish> list = presetDishMapper.selectList(
            new LambdaQueryWrapper<FamilymenuPresetDish>()
                .eq(StringUtils.hasText(category) && !"全部".equals(category),
                    FamilymenuPresetDish::getCategory, category)
                .orderByAsc(FamilymenuPresetDish::getId));

        if (CollectionUtils.isEmpty(list)) {
            return List.of();
        }
        return list.stream().map(preset -> {
            FamilymenuPresetDishResp resp = new FamilymenuPresetDishResp();
            BeanUtils.copyProperties(preset, resp);
            return resp;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importPresetDish(Long presetId, Long roomId) {
        accessSupport.requireMember(roomId);

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
        accessSupport.requireMember(roomId);

        List<FamilymenuPresetDish> presets = presetDishMapper.selectList(
            new LambdaQueryWrapper<FamilymenuPresetDish>()
                .orderByAsc(FamilymenuPresetDish::getId));

        if (CollectionUtils.isEmpty(presets)) {
            return;
        }

        List<FamilymenuDish> dishes = new ArrayList<>(presets.size());
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
