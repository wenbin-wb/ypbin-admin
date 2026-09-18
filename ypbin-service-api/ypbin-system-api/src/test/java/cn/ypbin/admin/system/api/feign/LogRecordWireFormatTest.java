/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.api.feign;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.json.autoconfigure.JacksonAutoConfiguration;
import cn.ypbin.starter.log.model.LogRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 日志上报的线上格式测试：{@link LogRecord} 是 {@code /internal/log-ingest} 的请求体，
 * 必须能被工程实际的 Jackson 定制（starter {@code ypbinJacksonCustomizer}）无损往返——
 * 否则 auth/ai 上报的日志会在 system 侧反序列化失败或丢字段，而这类问题只有在真机联调时才暴露。
 *
 * <p>边界：这里验证的是"同一套 Jackson 定制下的往返"，不覆盖 Feign 编解码器装配与网络传输
 * （无 Nacos/DB 环境，未做端到端验证）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class LogRecordWireFormatTest {

    /**
     * 采集时间字段是 {@code Instant}（不是工程约定的 {@code LocalDateTime} 格式），
     * 单独钉住它的往返，避免上报后 {@code sys_log.operate_time} 落空或错位。
     */
    @Test
    void collectedRecordShouldSurviveProjectJacksonRoundTrip() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                JsonMapperBuilderCustomizer customizer =
                    context.getBean(JsonMapperBuilderCustomizer.class);
                JsonMapper.Builder builder = JsonMapper.builder();
                customizer.customize(builder);
                ObjectMapper mapper = builder.build();

                LogRecord record = new LogRecord();
                record.setDescription("账号密码登录");
                record.setModule("认证");
                record.setRequestMethod("POST");
                record.setRequestUri("/login");
                record.setIp("10.0.0.8");
                record.setStatusCode(200);
                record.setUserId(42L);
                record.setTimestamp(Instant.parse("2026-09-16T02:30:00Z"));
                record.setTimeTakenMillis(37L);
                record.setSuccess(true);

                LogRecord restored = mapper.readValue(mapper.writeValueAsString(record),
                    LogRecord.class);

                // 字段同名往返，无改名映射（铁律 1）
                assertThat(restored.getDescription()).isEqualTo("账号密码登录");
                assertThat(restored.getModule()).isEqualTo("认证");
                assertThat(restored.getRequestMethod()).isEqualTo("POST");
                assertThat(restored.getRequestUri()).isEqualTo("/login");
                assertThat(restored.getIp()).isEqualTo("10.0.0.8");
                assertThat(restored.getStatusCode()).isEqualTo(200);
                assertThat(restored.getUserId()).isEqualTo(42L);
                assertThat(restored.getTimeTakenMillis()).isEqualTo(37L);
                assertThat(restored.isSuccess()).isTrue();
                assertThat(restored.getTimestamp()).isEqualTo(Instant.parse("2026-09-16T02:30:00Z"));
            });
    }
}
