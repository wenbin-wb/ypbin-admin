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

import cn.ypbin.starter.log.core.IpLocationResolver;
import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link IpLocationConfiguration} 的装配测试。
 *
 * <p><b>为什么需要它</b>：starter 的 {@code LogAutoConfiguration} 带一个默认的空解析器
 * （{@code @ConditionalOnMissingBean}）。若本配置没有先注册，启动不会报错，只是所有归属地悄悄
 * 变成空——正是这类「不报错的缺失」需要用例钉住：容器里必须是我们这一份实现。</p>
 *
 * @author wenbin
 * @since 2026-11-08
 */
class IpLocationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(Ip2regionSearcher.class, () -> mock(Ip2regionSearcher.class))
        .withUserConfiguration(IpLocationConfiguration.class);

    @Test
    @DisplayName("装配后容器中的 IpLocationResolver 是本仓实现，而不是 starter 的默认空实现")
    void configurationShouldRegisterRealResolver() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IpLocationResolver.class);
            assertThat(context.getBean(IpLocationResolver.class))
                .isInstanceOf(Ip2regionLocationResolver.class);
        });
    }
}
