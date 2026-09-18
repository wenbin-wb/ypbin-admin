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

/**
 * 合成玩具类：本模块测试源码<b>自带</b>的 {@code provider} 层类型，只用来给规则自检提供依赖落点。
 *
 * <p><strong>为什么必须自造而不是复用真实主源码类</strong>（2026-09-18 真实故障的根因）：
 * {@code ypbin-system} 用 {@code spring-boot-maven-plugin:repackage} 且<b>未配 classifier</b>，
 * 于是它在反应堆里产出的主构件是 {@code BOOT-INF/classes/...} 布局的 fat jar，{@code cn.ypbin.admin.system.*}
 * <b>不在 jar 根</b>；下游模块若去引用它的具体类（或裸 {@code -pl} 时命中 {@code ~/.m2} 里的旧 jar），
 * {@code testCompile} 就会报 {@code package cn.ypbin.admin.system.provider does not exist}，
 * 直接把整仓构建与部署打断。夹具改用自包含的玩具类后，门禁的编译期依赖只剩本模块自身。</p>
 *
 * <p>注意：本类在测试源码里，而门禁的字节码导入带 {@code DO_NOT_INCLUDE_TESTS}、源码规则只扫
 * {@code src/main/java}，故它不会把门禁自己搞红。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
@SuppressWarnings("unused")
public class SomeProvider {

    /**
     * 玩具端口方法：只为给夹具一个可调用的签名，无任何业务语义。
     */
    public void adapt() {
    }
}
