/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service.impl;

import cn.ypbin.admin.modules.system.entity.SysDict;
import cn.ypbin.admin.modules.system.entity.SysDictItem;
import cn.ypbin.admin.modules.system.mapper.SysDictItemMapper;
import cn.ypbin.admin.modules.system.mapper.SysDictMapper;
import cn.ypbin.admin.modules.system.model.query.DictQuery;
import cn.ypbin.admin.modules.system.model.req.DictSaveReq;
import cn.ypbin.admin.modules.system.model.resp.DictResp;
import cn.ypbin.admin.modules.system.service.SysDictService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import cn.ypbin.starter.json.dict.DictUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 字典类型服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
@RequiredArgsConstructor
public class SysDictServiceImpl extends BaseServiceImpl<SysDictMapper, SysDict> implements SysDictService {

    private final SysDictItemMapper dictItemMapper;

    @Override
    public PageResult<DictResp> pageDicts(DictQuery query) {
        PageResult<SysDict> source = page(query, new LambdaQueryWrapper<SysDict>()
            .like(StringUtils.hasText(query.getName()), SysDict::getName, query.getName())
            .like(StringUtils.hasText(query.getCode()), SysDict::getCode, query.getCode())
            .orderByDesc(SysDict::getCreateTime));
        List<DictResp> items = source.getItems().stream().map(this::toResp).toList();
        return PageResult.of(items, source.getTotal(), source.getPage(), source.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createDict(DictSaveReq req) {
        checkCodeUnique(req.getCode(), null);
        SysDict dict = new SysDict();
        BeanUtils.copyProperties(req, dict);
        save(dict);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDict(Long id, DictSaveReq req) {
        if (getById(id) == null) {
            throw new BusinessException("字典不存在");
        }
        checkCodeUnique(req.getCode(), id);
        SysDict dict = new SysDict();
        BeanUtils.copyProperties(req, dict);
        dict.setId(id);
        updateById(dict);
        // 编码可能变更，刷新字典缓存
        DictUtils.refresh();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDict(Long id) {
        removeById(id);
        dictItemMapper.delete(new LambdaQueryWrapper<SysDictItem>().eq(SysDictItem::getDictId, id));
        DictUtils.refresh();
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = exists(new LambdaQueryWrapper<SysDict>()
            .eq(SysDict::getCode, code)
            .ne(excludeId != null, SysDict::getId, excludeId));
        if (exists) {
            throw new BusinessException("字典编码已存在：" + code);
        }
    }

    private DictResp toResp(SysDict dict) {
        DictResp resp = new DictResp();
        BeanUtils.copyProperties(dict, resp);
        return resp;
    }
}
