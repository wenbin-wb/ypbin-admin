/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 用户名/手机号全局查重语句的数据权限放行机制测试。
 *
 * <p>「在数据范围之外查重」不能靠 {@code TenantContext.executeIgnore} 实现——那只关租户过滤。
 * 数据权限是 MyBatis-Plus 拦截器级行为（{@code DataPermissionContext} 只有进入/退出、无挂起语义，
 * 外层已激活时内层 {@code @DataPermission(ignore = true)} 也不会让上下文失效），
 * 只能由语句上的 {@code @InterceptorIgnore(dataPermission = "true")} 放行。</p>
 *
 * <p><b>证明方式</b>：本测试用 {@link MybatisConfiguration#addMapper(Class)} 走 MyBatis 真正的
 * Mapper 解析路径（{@code MybatisMapperAnnotationBuilder#parse} 会为每个方法登记
 * {@code @InterceptorIgnore}，已由字节码核实），再断言 MyBatis-Plus 自己的判据
 * {@link InterceptorIgnoreHelper#willIgnoreDataPermission(String)}——
 * {@code DataPermissionInterceptor#beforeQuery} 与 {@code #beforePrepare} 的首行判断的就是它。
 * 因此该断言与运行期「拦截器是否真的跳过本语句」是同一个条件，而不是对注解存在性的间接推断。</p>
 *
 * <p>另两个用例是反向证明：未标注的语句与不存在的语句都必须为 {@code false}，
 * 否则说明判据永真（例如写错语句 id 前缀），测试会「假绿」。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class SysUserMapperInterceptorIgnoreTest {

    private static final String GLOBAL_COUNT_STATEMENT =
        SysUserMapper.class.getName() + ".countByUsernameGlobal";

    private static final String GLOBAL_PHONE_COUNT_STATEMENT =
        SysUserMapper.class.getName() + ".countByPhoneGlobal";

    private static final String PLAIN_STATEMENT = SysUserMapper.class.getName() + ".insertBatch";

    private static void parseMapper() {
        // 走真实解析路径登记 @InterceptorIgnore（需要 GlobalConfig，MybatisConfiguration 自带默认值）
        new MybatisConfiguration().addMapper(SysUserMapper.class);
    }

    @Test
    @DisplayName("用户名全局查重语句必须跳过数据权限拦截器（跨部门重名才查得到）")
    void globalUsernameCountShouldSkipDataPermission() {
        parseMapper();

        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(GLOBAL_COUNT_STATEMENT))
            .as("该语句未跳过数据权限 ⇒ 查重仍落在部门范围内，跨部门重名会漏检并抛原始 SQL 错误")
            .isTrue();
    }

    @Test
    @DisplayName("手机号全局查重语句必须跳过数据权限拦截器（跨部门重号才查得到）")
    void globalPhoneCountShouldSkipDataPermission() {
        parseMapper();

        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(GLOBAL_PHONE_COUNT_STATEMENT))
            .as("该语句未跳过数据权限 ⇒ 手机号查重仍落在部门范围内，跨部门重号会漏检并抛原始 SQL 错误")
            .isTrue();
    }

    @Test
    @DisplayName("反向证明：未标注的语句不得被放行（判据不是永真）")
    void plainStatementsShouldStillBeGoverned() {
        parseMapper();

        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(PLAIN_STATEMENT))
            .as("未标注 @InterceptorIgnore 的语句被判为可跳过 ⇒ 数据权限被整体打穿，属严重越权")
            .isFalse();
    }

    @Test
    @DisplayName("反向证明：不存在的语句 id 不得被放行")
    void unknownStatementShouldNotBeIgnored() {
        parseMapper();

        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(SysUserMapper.class.getName()
            + ".notExistsStatement"))
            .isFalse();
    }
}
