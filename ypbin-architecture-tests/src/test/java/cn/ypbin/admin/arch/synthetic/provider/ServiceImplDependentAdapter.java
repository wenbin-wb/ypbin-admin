/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.arch.synthetic.provider;

import cn.ypbin.admin.arch.synthetic.service.impl.SupportOnlyDependency;
import java.util.List;

/**
 * 合成样例：{@code provider} 依赖 {@code service/impl}（<b>反向</b>依赖）。
 *
 * <p>用来断言「{@code service/impl} 禁依赖 {@code provider}」这条规则<b>方向性正确</b>：
 * 只拦单向，不能把反向依赖一起判违规（否则规则与注释声称的语义不符，
 * 未来真需要反向复用时会被误拦）。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@SuppressWarnings("unused")
public class ServiceImplDependentAdapter {

    /**
     * 反向依赖：适配器调用实现层的静态方法。
     *
     * @return 空列表
     */
    public List<String> codes() {
        return SupportOnlyDependency.emptyCodes();
    }
}
