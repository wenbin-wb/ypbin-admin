/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

/**
 * {@link AiChatSseSupport} 的职责收敛测试（对应「ai 模块重复实现 SseStreamBridge」的整改）。
 *
 * <p><b>修的是什么</b>：{@code errorEmitter} 与 {@code rootMessage} 曾是 starter
 * {@code SseStreamBridge} 的逐行副本（starter 版另有参数校验与可定制选项），两份实现必然漂移。
 * 整改后本类<b>只保留</b> starter 未提供的 {@code disposeQuietly}（类型不同：{@code AtomicReference}
 * vs {@code Disposable.Swap}），其余改调 starter。</p>
 *
 * <p>本测试用反射锁定「副本不会再被加回来」——这类回归没有编译告警，只能靠用例挡。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class AiChatSseSupportTest {

    @Test
    @DisplayName("本类只暴露 disposeQuietly 一个静态方法（重复实现不得回归）")
    void shouldOnlyDeclareDisposeQuietly() {
        List<String> declaredMethods = Arrays.stream(AiChatSseSupport.class.getDeclaredMethods())
            .filter(method -> Modifier.isStatic(method.getModifiers()))
            .filter(method -> !method.isSynthetic())
            .map(Method::getName)
            .toList();

        assertThat(declaredMethods)
            .as("errorEmitter/rootMessage 已由 starter 的 SseStreamBridge 提供，"
                + "在本类重新出现即意味着又抄了一份副本（会与 starter 漂移）")
            .containsExactly("disposeQuietly");
    }

    @Test
    @DisplayName("disposeQuietly：有订阅时 dispose 一次")
    void disposeQuietlyShouldDisposeWhenPresent() {
        Disposable disposable = mock(Disposable.class);
        AtomicReference<Disposable> ref = new AtomicReference<>(disposable);

        AiChatSseSupport.disposeQuietly(ref);

        verify(disposable).dispose();
    }

    @Test
    @DisplayName("disposeQuietly：空引用不抛 NPE（流式链路可能在订阅建立前就出错）")
    void disposeQuietlyShouldTolerateAbsentSubscription() {
        AtomicReference<Disposable> empty = new AtomicReference<>();

        AiChatSseSupport.disposeQuietly(empty);

        // 没有订阅可释放，方法必须安静返回（幂等语义）；此处不 mock 任何对象，能跑完即证明未抛 NPE
        assertThat(empty.get()).isNull();
    }
}
