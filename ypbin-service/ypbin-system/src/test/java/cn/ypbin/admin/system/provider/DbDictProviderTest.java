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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysDictItem;
import cn.ypbin.admin.system.mapper.SysDictItemMapper;
import cn.ypbin.starter.json.dict.DictCache;
import cn.ypbin.starter.json.dict.DictItem;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 字典翻译数据源测试。
 *
 * <p>覆盖四类边界：<strong>命中</strong>（value→label、color/sort 透传）、<strong>查不到</strong>与
 * <strong>空字典</strong>（回退为原始 code，而非空串）、<strong>空入参</strong>（不查库）、
 * 以及<strong>脏数据</strong>（label 空白的字典项不得把原值掩盖成空串）。</p>
 *
 * <p>说明：{@code sys_dict_item} 表结构（{@code deploy/sql/001-schema.sql:180-200}）只有单一
 * {@code label} 列，没有 locale/多语言列，{@link DictItem} 也只有 value/label/color/sort，因此
 * "多语言字段选择"在本仓库无对应字段，展示文本只能取 {@code label}；相关结论已在此以用例固化。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class DbDictProviderTest {

    private SysDictItem item(Long id, String value, String label, String color, Integer sort) {
        SysDictItem item = new SysDictItem();
        item.setId(id);
        item.setValue(value);
        item.setLabel(label);
        item.setColor(color);
        item.setSort(sort);
        return item;
    }

    @Test
    void shouldMapValueLabelColorAndSort() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectByDictCode("sys_gender"))
            .thenReturn(List.of(item(22L, "1", "男", "processing", 2)));

        List<DictItem> items = new DbDictProvider(mapper).getItems("sys_gender");

        assertThat(items).hasSize(1);
        DictItem dictItem = items.getFirst();
        assertThat(dictItem.getValue()).isEqualTo("1");
        assertThat(dictItem.getLabel()).isEqualTo("男");
        assertThat(dictItem.getColor()).isEqualTo("processing");
        assertThat(dictItem.getSort()).isEqualTo(2);
        // 一次批量取数，不在循环内查库
        verify(mapper).selectByDictCode("sys_gender");
    }

    @Test
    void shouldDefaultSortToZeroWhenNull() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectByDictCode("sys_status"))
            .thenReturn(List.of(item(11L, "1", "正常", "success", null)));

        // sys_dict_item.sort 列可空（schema:188），DB 为 NULL 时不得 NPE
        assertThat(new DbDictProvider(mapper).getItems("sys_status").getFirst().getSort()).isZero();
    }

    @Test
    void shouldSkipItemWithBlankLabelSoRawValueIsNotMasked() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectByDictCode("sys_status"))
            .thenReturn(List.of(item(11L, "1", "  ", "success", 1), item(12L, "0", "禁用", "error", 2)));

        List<DictItem> items = new DbDictProvider(mapper).getItems("sys_status");

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getValue()).isEqualTo("0");
    }

    @Test
    void shouldSkipItemWithBlankValue() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectByDictCode("sys_status"))
            .thenReturn(List.of(item(11L, " ", "正常", "success", 1)));

        assertThat(new DbDictProvider(mapper).getItems("sys_status")).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWithoutQueryOnBlankDictType() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        DbDictProvider provider = new DbDictProvider(mapper);

        assertThat(provider.getItems(null)).isEmpty();
        assertThat(provider.getItems("")).isEmpty();
        assertThat(provider.getItems("   ")).isEmpty();
        // 空入参必须短路，绝不能把空白编码带进 SQL
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldReturnEmptyListWhenDictHasNoItems() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectByDictCode(anyString())).thenReturn(List.of());

        assertThat(new DbDictProvider(mapper).getItems("sys_gender")).isEmpty();
    }

    /**
     * 查不到的键与空字典都必须回退为原始 code（starter 侧 getOrDefault 语义），不得是空串。
     */
    @Test
    void shouldFallBackToRawCodeWhenKeyNotFoundOrDictEmpty() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectByDictCode("sys_gender")).thenReturn(List.of(item(22L, "1", "男", null, 2)));
        when(mapper.selectByDictCode("sys_empty")).thenReturn(List.of());
        DictCache cache = new DictCache(new DbDictProvider(mapper));

        assertThat(cache.translate("sys_gender", "1")).isEqualTo("男");
        // 字典存在但键查不到：回退原值
        assertThat(cache.translate("sys_gender", "9")).isEqualTo("9");
        // 空字典：回退原值
        assertThat(cache.translate("sys_empty", "1")).isEqualTo("1");
        // null 值原样返回
        assertThat(cache.translate("sys_gender", null)).isNull();
    }

    /**
     * 展示文本取 {@code label}（本表唯一的展示列，无多语言列可选）。
     */
    @Test
    void shouldUseLabelAsDisplayTextSinceSchemaHasNoLocaleColumn() {
        SysDictItemMapper mapper = mock(SysDictItemMapper.class);
        when(mapper.selectByDictCode("sys_gender")).thenReturn(List.of(item(23L, "2", "女", "warning", 3)));

        assertThat(new DbDictProvider(mapper).getItems("sys_gender").getFirst().getLabel()).isEqualTo("女");
    }
}
