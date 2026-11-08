/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import net.dreamlu.mica.ip2region.core.IpInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link Ip2regionLocationResolver} 的解析口径测试。
 *
 * <p><b>为什么需要它</b>：归属地只用于展示，解析错了不会报错，只会让在线用户/操作日志显示错误的
 * 地点；而「本机/内网返回内网」「无法解析返回 null」这两条边界决定了页面显示什么。故把判据钉住。</p>
 *
 * @author wenbin
 * @since 2026-11-08
 */
class Ip2regionLocationResolverTest {

    private final Ip2regionSearcher searcher = mock(Ip2regionSearcher.class);

    private final Ip2regionLocationResolver resolver = new Ip2regionLocationResolver(searcher);

    @Test
    @DisplayName("空 IP：返回 null，不触碰离线库")
    void blankIpShouldReturnNull() {
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve("")).isNull();
        assertThat(resolver.resolve("   ")).isNull();
        verifyNoInteractions(searcher);
    }

    @Test
    @DisplayName("本机/回环：统一返回「内网」，不触碰离线库")
    void loopbackIpShouldReturnIntranet() {
        assertThat(resolver.resolve("127.0.0.1")).isEqualTo("内网");
        assertThat(resolver.resolve("0:0:0:0:0:0:0:1")).isEqualTo("内网");
        assertThat(resolver.resolve("::1")).isEqualTo("内网");
        assertThat(resolver.resolve("localhost")).isEqualTo("内网");
        verifyNoInteractions(searcher);
    }

    @Test
    @DisplayName("公网 IP：返回离线库拼出的「国家 省 市 ISP」")
    void publicIpShouldReturnAddressAndIsp() {
        IpInfo info = new IpInfo();
        info.setCountry("中国");
        info.setProvince("广东省");
        info.setCity("深圳市");
        info.setIsp("电信");
        when(searcher.memorySearch("1.2.3.4")).thenReturn(info);

        assertThat(resolver.resolve("1.2.3.4")).isEqualTo("中国 广东省 深圳市 电信");
    }

    @Test
    @DisplayName("离线库无结果或地址为空：返回 null（留空而不是臆造）")
    void unknownIpShouldReturnNull() {
        when(searcher.memorySearch("8.8.8.8")).thenReturn(null);
        when(searcher.memorySearch("9.9.9.9")).thenReturn(new IpInfo());

        assertThat(resolver.resolve("8.8.8.8")).isNull();
        assertThat(resolver.resolve("9.9.9.9")).isNull();
    }
}
