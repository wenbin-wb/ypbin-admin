/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.arch.synthetic.service.impl;

import java.util.List;

/**
 * 合成合规样例：{@code service/impl} 只依赖非 {@code provider} 的类型。
 *
 * <p>存在的意义是给规则一个「必须放过」的对照物——若规则写成恒真（例如包匹配写错、
 * 或把「依赖任何东西」都判违规），本类会立刻把门禁搞红。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@SuppressWarnings("unused")
public class SupportOnlyDependency {

    /** 合规依赖：JDK 类型，不属于任何 provider 包 */
    private List<String> codes;

    /**
     * 反向依赖的落点：{@code provider} 里的合成适配器依赖本类，规则必须放过。
     *
     * @return 空列表
     */
    public static List<String> emptyCodes() {
        return List.of();
    }
}
