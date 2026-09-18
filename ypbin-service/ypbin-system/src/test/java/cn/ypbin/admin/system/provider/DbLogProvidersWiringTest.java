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
import static org.mockito.Mockito.mock;

import cn.ypbin.admin.system.mapper.SysLogMapper;
import cn.ypbin.starter.log.aspect.LogAspect;
import cn.ypbin.starter.log.autoconfigure.LogAutoConfiguration;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.log.dao.DefaultLogDao;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.event.LogEventListener;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 操作日志装配测试：锁定"业务方提供 {@link LogDao} 时 starter 的 {@link DefaultLogDao} 必须退让"。
 *
 * <p>这正是本任务的静默失效机制——{@code LogAutoConfiguration.logDao()} 带
 * {@code @ConditionalOnMissingBean}（starter 3.3.0 {@code LogAutoConfiguration.java:61-65}），
 * 没人提供实现时安静地装配"只打印不落库"的默认实现。若本仓的 {@code DbLogDao} 因装配顺序或
 * 包扫描范围没生效，接口仍能跑通、只是 {@code sys_log} 恒为空，纯靠人工很难发现，故用装配测试钉住。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class DbLogProvidersWiringTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class));

    @Test
    void userLogDaoShouldOverrideStarterDefault() {
        runner.withUserConfiguration(UserLogConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            // 只能有一个 LogDao，且必须是落库实现（DefaultLogDao 退让）
            assertThat(context).hasSingleBean(LogDao.class);
            assertThat(context).getBean(LogDao.class).isInstanceOf(DbLogProviders.DbLogDao.class);
            assertThat(context).getBean(LogUserProvider.class)
                .isInstanceOf(DbLogProviders.IdentityLogUserProvider.class);
            // 切面与监听器由 starter 提供，本仓只补数据源，不重复造
            assertThat(context).hasSingleBean(LogAspect.class);
            assertThat(context).hasSingleBean(LogEventListener.class);
        });
    }

    @Test
    void starterDefaultIsUsedWhenHostProvidesNothing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).getBean(LogDao.class).isInstanceOf(DefaultLogDao.class);
        });
    }

    /**
     * 模拟本仓的组件扫描结果：两个 Bean 均为 {@code @Component}，此处显式注册。
     */
    @Configuration
    static class UserLogConfig {

        @Bean
        DbLogProviders.DbLogDao dbLogDao() {
            return new DbLogProviders.DbLogDao(mock(SysLogMapper.class));
        }

        @Bean
        DbLogProviders.IdentityLogUserProvider identityLogUserProvider() {
            return new DbLogProviders.IdentityLogUserProvider();
        }
    }
}
