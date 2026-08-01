/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.provider;

import cn.ypbin.admin.system.mapper.SysDictItemMapper;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 字典数据源：从字典表读取字典项，接通 starter 的 {@code @DictText} 自动翻译。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Component
@RequiredArgsConstructor
public class DbDictProvider implements DictProvider {

    private final SysDictItemMapper dictItemMapper;

    @Override
    public List<DictItem> getItems(String dictType) {
        return dictItemMapper.selectByDictCode(dictType).stream()
            .map(item -> {
                DictItem dictItem = new DictItem(item.getValue(), item.getLabel());
                dictItem.setColor(item.getColor());
                dictItem.setSort(item.getSort() == null ? 0 : item.getSort());
                return dictItem;
            })
            .toList();
    }
}
