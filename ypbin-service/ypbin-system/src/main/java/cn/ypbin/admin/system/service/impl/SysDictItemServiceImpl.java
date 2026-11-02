/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.entity.SysDict;
import cn.ypbin.admin.system.entity.SysDictItem;
import cn.ypbin.admin.system.mapper.SysDictItemMapper;
import cn.ypbin.admin.system.mapper.SysDictMapper;
import cn.ypbin.admin.system.model.req.DictItemSaveReq;
import cn.ypbin.admin.system.model.resp.DictItemResp;
import cn.ypbin.admin.system.service.SysDictItemService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.json.dict.DictUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典项服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysDictItemServiceImpl extends BaseServiceImpl<SysDictItemMapper, SysDictItem>
    implements SysDictItemService {

    private final SysDictMapper dictMapper;

    @Override
    public List<DictItemResp> listByDictId(Long dictId) {
        return list(new LambdaQueryWrapper<SysDictItem>()
            .eq(SysDictItem::getDictId, dictId)
            .orderByAsc(SysDictItem::getSort))
            .stream().map(this::toResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createItem(DictItemSaveReq req) {
        requireDict(req.getDictId());
        checkValueUnique(req.getDictId(), req.getValue(), null);
        SysDictItem item = new SysDictItem();
        BeanUtils.copyProperties(req, item);
        if (!save(item)) {
            throw new BusinessException("字典项新增失败");
        }
        refreshDict(req.getDictId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long id, DictItemSaveReq req) {
        SysDictItem existing = getById(id);
        if (existing == null) {
            throw new BusinessException("字典项不存在");
        }
        requireDict(req.getDictId());
        checkValueUnique(req.getDictId(), req.getValue(), id);
        SysDictItem item = new SysDictItem();
        BeanUtils.copyProperties(req, item);
        item.setId(id);
        if (!updateById(item)) {
            throw new BusinessException("字典项更新失败");
        }
        // 换字典时旧字典的缓存也必须刷新，否则 @DictText 仍会翻译出已移走的项
        refreshDict(existing.getDictId());
        if (!Objects.equals(existing.getDictId(), req.getDictId())) {
            refreshDict(req.getDictId());
        }
    }

    /**
     * 校验目标字典存在，防止把字典项挂到不存在的字典上（孤儿项）。
     *
     * @param dictId 字典 ID
     */
    private void requireDict(Long dictId) {
        if (dictId == null || dictMapper.selectById(dictId) == null) {
            throw new BusinessException("字典不存在：" + dictId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        SysDictItem item = getById(id);
        if (item == null) {
            throw new BusinessException("字典项不存在");
        }
        if (!removeById(id)) {
            throw new BusinessException("字典项删除失败");
        }
        refreshDict(item.getDictId());
    }

    private void checkValueUnique(Long dictId, String value, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysDictItem>()
            .eq(SysDictItem::getDictId, dictId)
            .eq(SysDictItem::getValue, value)
            .ne(excludeId != null, SysDictItem::getId, excludeId));
        if (exists) {
            throw new BusinessException("字典项值已存在：" + value);
        }
    }

    /**
     * 按字典编码刷新 starter 字典缓存，使 {@code @DictText} 翻译即时生效。
     */
    private void refreshDict(Long dictId) {
        SysDict dict = dictMapper.selectById(dictId);
        if (dict != null) {
            DictUtils.refresh(dict.getCode());
        }
    }

    private DictItemResp toResp(SysDictItem item) {
        DictItemResp resp = new DictItemResp();
        BeanUtils.copyProperties(item, resp);
        return resp;
    }
}
