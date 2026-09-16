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
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRequestContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 埋点上报的线上格式测试：{@code List<TrackEvent>} 是 {@code /internal/track-ingest} 的请求体，
 * 必须能被工程实际的 Jackson 定制（starter {@code ypbinJacksonCustomizer}）无损往返。
 *
 * <p>为什么必须单独钉住：{@link TrackEvent} 是<b>record</b>，且带 {@code Instant} +
 * {@code Map<String,Object>} payload + 可空的 {@link TrackRequestContext}，构造期还有必填校验
 * （{@code TrackEvent.java:93-99} 的 {@code Objects.requireNonNull} 与 {@code Map.copyOf}）。
 * auth 与 system 两侧的 Jackson 定制或 record 参数名一旦漂移，只会在真机联调时以反序列化失败暴露——
 * 而埋点失败是旁路能力，线上很容易被忽略成「漏斗里就是没有这一步」。</p>
 *
 * <p>边界：只验证"同一套 Jackson 定制下的往返"，不覆盖 Feign 编解码器装配与网络传输
 * （无 Nacos/DB 环境，未做端到端验证）。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackEventWireFormatTest {

    @Test
    void trackEventShouldSurviveProjectJacksonRoundTrip() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                JsonMapperBuilderCustomizer customizer =
                    context.getBean(JsonMapperBuilderCustomizer.class);
                JsonMapper.Builder builder = JsonMapper.builder();
                customizer.customize(builder);
                ObjectMapper mapper = builder.build();

                TrackEvent event = new TrackEvent("evt-1", "auth.user.login",
                    Instant.parse("2026-09-16T02:30:00Z"), "ypbin-admin-auth", null, null, null, null,
                    null, true, Map.of("authType", "ACCOUNT"),
                    new TrackRequestContext("10.0.0.8", "junit-agent", "trace-1", 42L, 7L));

                String json = mapper.writeValueAsString(List.of(event));
                List<TrackEvent> restored = mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, TrackEvent.class));

                assertThat(restored).hasSize(1);
                TrackEvent round = restored.getFirst();
                // 字段同名往返，无改名映射（铁律 1）
                assertThat(round.eventId()).isEqualTo("evt-1");
                assertThat(round.eventCode()).isEqualTo("auth.user.login");
                assertThat(round.eventTime()).isEqualTo(Instant.parse("2026-09-16T02:30:00Z"));
                assertThat(round.appId()).isEqualTo("ypbin-admin-auth");
                assertThat(round.success()).isTrue();
                assertThat(round.payload()).containsExactly(Map.entry("authType", "ACCOUNT"));
                assertThat(round.context()).isNotNull();
                assertThat(round.context().clientIp()).isEqualTo("10.0.0.8");
                assertThat(round.context().userAgent()).isEqualTo("junit-agent");
                assertThat(round.context().traceId()).isEqualTo("trace-1");
                assertThat(round.context().userId()).isEqualTo(42L);
                assertThat(round.context().tenantId()).isEqualTo(7L);
            });
    }

    /**
     * 无请求上下文的构造器（后端切面/IoT 场景）序列化后 {@code context} 必须是 {@code null}，
     * 不得被写成空对象——否则落库会出现一堆无意义的空维度。
     */
    @Test
    void eventWithoutContextShouldKeepNullContext() {
        TrackEvent event = new TrackEvent("evt-2", "auth.user.logout",
            Instant.parse("2026-09-16T02:31:00Z"), null, null, null, null, null, null, true, Map.of());

        assertThat(event.context()).isNull();
        assertThat(event.payload()).isEmpty();
    }
}
