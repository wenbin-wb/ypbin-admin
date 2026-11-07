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

import cn.ypbin.starter.security.core.AbstractPermissionProvider;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 权限数据源「薄适配」形态的回归护栏（对应 starter #101）。
 *
 * <p><strong>钉住什么</strong>：3 个 {@code *PermissionProvider} 必须继承 starter 的
 * {@link AbstractPermissionProvider}，且类里<b>只能有</b>两个数据源方法
 * （{@code permissionsOf}/{@code rolesOf}）。</p>
 *
 * <p><strong>为什么需要它</strong>：整改前每个 Provider 都自带一份逐字节相同的
 * {@code resolveUserId} + {@code getPermissions}/{@code getRoles} 骨架（本仓 3 份、加 iot 共 4 份）。
 * 这类「复制粘贴」回归没有任何编译/测试告警——把骨架抄回来，现有用例照样全绿（行为确实一样），
 * 但口径分叉的风险随之回来。故用反射把「骨架不得回流」固化成断言。</p>
 *
 * <p>本类用 system 的实现做代表断言；auth / ai 两个实现由各自的既有测试类覆盖其行为，
 * 三者在此情形下结构一致（同样的父类、同样的两个方法）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class PermissionProviderSkeletonTest {

    /** 基类要求宿主实现的数据源方法（白名单） */
    private static final List<String> EXPECTED_DATA_SOURCE_METHODS = List.of("permissionsOf", "rolesOf");

    @Test
    @DisplayName("Provider 继承 starter 抽象基类（不直接实现接口、不自带骨架）")
    void providerShouldExtendStarterBaseClass() {
        assertThat(AbstractPermissionProvider.class.isAssignableFrom(SystemPermissionProvider.class))
            .as("必须继承 cn.ypbin.starter.security.core.AbstractPermissionProvider，"
                + "而不是自己实现 PermissionProvider 并抄一份 resolveUserId 骨架")
            .isTrue();
        assertThat(PermissionProvider.class.isAssignableFrom(SystemPermissionProvider.class))
            .as("继承基类后仍满足 starter 端口契约")
            .isTrue();
    }

    @Test
    @DisplayName("类内只声明两个数据源方法（loginId 解析/getPermissions/getRoles 骨架不得回流）")
    void shouldOnlyDeclareTwoDataSourceMethods() {
        List<String> declared = Arrays.stream(SystemPermissionProvider.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .map(Method::getName)
            .sorted()
            .toList();

        assertThat(declared)
            .as("骨架方法（getPermissions/getRoles/resolveUserId）一旦回流，说明又抄了一份 starter 基类的实现")
            .containsExactlyElementsOf(EXPECTED_DATA_SOURCE_METHODS.stream().sorted().toList());
    }

    @Test
    @DisplayName("两个数据源方法必须是 protected 覆写（不是 public 的独立实现）")
    void dataSourceMethodsShouldBeOverrides() throws NoSuchMethodException {
        for (String name : EXPECTED_DATA_SOURCE_METHODS) {
            Method method = SystemPermissionProvider.class.getDeclaredMethod(name, long.class);
            assertThat(Modifier.isProtected(method.getModifiers()))
                .as("%s 应是 protected 覆写（基类为 protected abstract）", name)
                .isTrue();
            assertThat(method.getDeclaringClass()).isEqualTo(SystemPermissionProvider.class);
        }
    }
}
