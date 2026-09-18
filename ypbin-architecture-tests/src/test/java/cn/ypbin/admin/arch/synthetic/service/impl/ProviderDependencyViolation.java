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

import cn.ypbin.admin.arch.synthetic.provider.SomeProvider;

/**
 * 合成违规样例：{@code service/impl} 直接依赖 {@code provider} 的具体适配实现类型。
 *
 * <p>复刻的是 2026-09-17 那次真实回归的形态——{@code SysUserServiceImpl} 曾直接持有
 * {@code provider.AdminDataScopeHandler} 来复用数据范围判定；被禁止的是「实现层直连适配实现类」这一
 * <b>依赖方向</b>，而不是某个特定类，故用本模块自带的 {@code provider} 玩具类即可等强度地表达。
 * 本类只存在于<b>测试源码</b>，门禁的字节码导入带 {@code DO_NOT_INCLUDE_TESTS}、源码规则只扫
 * {@code src/main/java}，故它不会反过来把门禁自己搞红。</p>
 *
 * <p><strong>为什么改成自造的玩具类</strong>（2026-09-18 真实故障）：本类曾直接 import
 * {@code cn.ypbin.admin.system.provider.AdminDataScopeHandler}，而 {@code ypbin-system} 的
 * {@code spring-boot-maven-plugin:repackage} 未配 classifier，主构件是 {@code BOOT-INF/classes/...}
 * 布局的 fat jar；裸 {@code -pl}（不带 {@code -am}）时还会命中 {@code ~/.m2} 里的旧 jar。
 * 任一形态下这条 import 都会让 {@code testCompile} 报 {@code package ... does not exist}，
 * 整仓构建失败、部署中断。夹具的职责只是「触发规则」，不该把跨模块真实类拖进<b>编译期</b>依赖。</p>
 *
 * <p>规则的语义与强度不因夹具自包含而改变：仍是包级通配的「{@code ..service.impl..} 不得依赖
 * {@code ..provider..}」，由 {@code ArchRuleSelfCheckTest} 逐条证明「合成违规转红 / 仅依赖 JDK 类型放过 /
 * 反向依赖放过」三点。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@SuppressWarnings("unused")
public class ProviderDependencyViolation {

    /** 违规依赖：实现层直连宿主端口适配实现 */
    private SomeProvider someProvider;
}
