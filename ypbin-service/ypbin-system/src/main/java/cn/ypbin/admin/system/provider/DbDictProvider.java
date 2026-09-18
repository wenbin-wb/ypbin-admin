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

import cn.ypbin.admin.system.entity.SysDictItem;
import cn.ypbin.admin.system.mapper.SysDictItemMapper;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictProvider;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 字典翻译数据源（数据库实现）。
 *
 * <p>starter 的 {@code @DictText} 机制是"有 {@link DictProvider} 才装配 {@code DictCache}"（{@code
 * JacksonAutoConfiguration} 里对该接口做了 {@code @ConditionalOnBean}，见 starter 3.3.0
 * {@code JacksonAutoConfiguration.java:133-141}）：<strong>没有 provider 时带 {@code @DictText}
 * 的字段不会输出派生文本字段，也不会报错</strong>。本类按 {@code sys_dict.code}（字典编码，即注解里的
 * dictType）批量读取 {@code sys_dict_item}，使 {@code @DictText} 真正产出 {@code xxxText}。</p>
 *
 * <p>取数走 {@link SysDictItemMapper#selectByDictCode}：一次查询取回该字典类型下的全部字典项，
 * 由框架的 {@code DictCache} 按类型缓存，<strong>不存在循环内查库</strong>。缓存失效无需本类参与：
 * 字典项增删改走 {@code SysDictItemServiceImpl#refreshDict}、字典类型变更走
 * {@code SysDictServiceImpl}，二者已调用 {@code DictUtils.refresh}。</p>
 *
 * <p><strong>查不到时的回退：</strong>回退逻辑在 starter 侧，本类不覆盖——{@code DictCache.translate}
 * 对未命中的 value 返回原值（{@code DictCache.java:71-72}），未接入字典时 {@code DictUtils.translate}
 * 同样返回原值（{@code DictUtils.java:64-66}）。因此"空字典/查不到的键"最终回退为<strong>原始 code</strong>，
 * 不会返回空串；本类只负责不把空 label 塞进缓存（见 {@link #toDictItem}），避免"命中空 label"把原值
 * 覆盖成空串而掩盖问题。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
@Component
@RequiredArgsConstructor
public class DbDictProvider implements DictProvider {

    private static final Logger log = LoggerFactory.getLogger(DbDictProvider.class);

    /** 字典项未配置排序时的默认序号 */
    private static final int DEFAULT_SORT = 0;

    private final SysDictItemMapper dictItemMapper;

    @Override
    public List<DictItem> getItems(String dictType) {
        // 先判空短路：避免向 SQL 传入空白字典编码，也避免无意义的查库
        if (dictType == null || dictType.isBlank()) {
            return List.of();
        }
        return dictItemMapper.selectByDictCode(dictType).stream()
            .map(this::toDictItem)
            .flatMap(Optional::stream)
            .toList();
    }

    /**
     * 字典项实体 → starter 字典项。
     *
     * <p>value 与 label 缺一不可：{@code DictCache.loadLabels} 会把 value→label 直接放入映射表，
     * 若 label 为空则 {@code translate} 会返回 null/空串，反而把原始 code 掩盖掉。故此处跳过这类脏数据，
     * 让翻译按"查不到"回退为原值，并留下告警暴露数据质量问题（不静默、也不因单条脏数据让整次翻译失败）。</p>
     *
     * @param item 字典项实体
     * @return 转换结果；字段缺失时为 {@link Optional#empty()}
     */
    private Optional<DictItem> toDictItem(SysDictItem item) {
        if (item.getValue() == null || item.getValue().isBlank()
            || item.getLabel() == null || item.getLabel().isBlank()) {
            log.warn("字典项缺少 value/label，已跳过翻译，dictItemId={}, value={}, label={}",
                item.getId(), item.getValue(), item.getLabel());
            return Optional.empty();
        }
        DictItem dictItem = new DictItem(item.getValue(), item.getLabel());
        dictItem.setColor(item.getColor());
        dictItem.setSort(item.getSort() == null ? DEFAULT_SORT : item.getSort());
        return Optional.of(dictItem);
    }
}
