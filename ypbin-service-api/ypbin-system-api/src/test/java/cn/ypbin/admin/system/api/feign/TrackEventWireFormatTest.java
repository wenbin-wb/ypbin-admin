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
        TrackEvent event = new TrackEvent("evt-1", "auth.user.login",
            Instant.parse("2026-09-16T02:30:00Z"), "ypbin-admin-auth", null, null, null, null,
            null, true, Map.of("authType", "ACCOUNT"),
            new TrackRequestContext("10.0.0.8", "junit-agent", "trace-1", 42L, 7L));

        trackEventRoundTrip(observed -> {
            TrackEvent round = observed.round();
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
            // Long → JSON 字符串 → 回 Long：跨服务最易碎的一环（starter 默认 writeBigNumberAsString=true）
            assertThat(round.context().userId()).isEqualTo(42L);
            assertThat(round.context().tenantId()).isEqualTo(7L);
        }, event);
    }

    /**
     * 无请求上下文的事件：<b>序列化后</b> {@code context} 必须写成 {@code null}、
     * 反序列化回来仍是 {@code null}——写成 {@code {}} 会让落库出现一堆无意义的空维度。
     *
     * <p>（复核意见：本用例原先只 new 对象断言 getter，全程没有序列化，等于没测到它自己声明要守的行为。）</p>
     */
    @Test
    void eventWithoutContextShouldSurviveRoundTripWithNullContext() {
        trackEventRoundTrip(event -> {
            String json = event.json();
            assertThat(json).contains("\"context\":null");
            // 字段齐全性：Long/Instant/Map 都在同一套定制下往返
            TrackEvent round = event.round();
            assertThat(round.eventId()).isEqualTo("evt-2");
            assertThat(round.eventCode()).isEqualTo("auth.user.logout");
            assertThat(round.eventTime()).isEqualTo(Instant.parse("2026-09-16T02:31:00Z"));
            assertThat(round.durationMs()).isEqualTo(37L);
            assertThat(round.payload()).isEmpty();
            assertThat(round.context()).isNull();
        }, new TrackEvent("evt-2", "auth.user.logout", Instant.parse("2026-09-16T02:31:00Z"),
            null, "sess-1", "anon-1", "https://example.com/page", "https://example.com/prev",
            37L, false, Map.of()));
    }

    /**
     * payload 里的 {@code Long} 经本工程的 Jackson 定制会<b>退化成字符串</b>
     * （{@code writeBigNumberAsString=true} 把 Long 写成 JSON 字符串，而 payload 是
     * {@code Map<String,Object>}，反序列化时无从还原具体类型）；{@code Integer} 与浮点<b>保持数字</b>。
     *
     * <p>本用例<b>刻意把这个现状钉住</b>：任何分析口径都不能假设 payload 的 Long 数值保型
     * （读出来是 {@code String}）。若产品要求保型，这条用例应作为「应当失败」的用例被显式修改，
     * 而不是被默默绕过。</p>
     *
     * <p>（复核意见指出：我第一版把探针里的 {@code 3L} 误写成 {@code 3}，于是断言了一个不存在的退化行为——
     * 这条用例现在同时钉住 Long 退化与 Integer 保型两侧。）</p>
     */
    @Test
    void longPayloadValueDegradesToStringWhileIntegerIsPreserved() {
        TrackEvent event = new TrackEvent("evt-3", "system.user.export",
            Instant.parse("2026-09-16T02:32:00Z"), null, null, null, null, null, 5L, true,
            Map.of("rowCount", 3L, "retryCount", 3, "ratio", 1.5));

        trackEventRoundTrip(observed -> {
            assertThat(observed.json()).contains("\"rowCount\":\"3\"");
            assertThat(observed.round().payload().get("rowCount")).isInstanceOf(String.class);
            assertThat(observed.round().payload().get("rowCount")).isEqualTo("3");
            // Integer 与浮点不退化：退化只发生在 Long（全局 Long→String 序列化策略）
            assertThat(observed.round().payload().get("retryCount")).isInstanceOf(Integer.class);
            assertThat(observed.round().payload().get("ratio")).isInstanceOf(Double.class);
            // 顶层 Long 字段（durationMs）同样走 Long→String 链路，但 record 组件类型明确，能还原
            assertThat(observed.round().durationMs()).isEqualTo(5L);
        }, event);
    }

    /** 用工程实际的 Jackson 定制做一次「序列化 → 反序列化」，把 JSON 与还原结果一起交给断言 */
    private static void trackEventRoundTrip(java.util.function.Consumer<Observed> assertion,
                                           TrackEvent event) {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                JsonMapperBuilderCustomizer customizer =
                    context.getBean(JsonMapperBuilderCustomizer.class);
                JsonMapper.Builder builder = JsonMapper.builder();
                customizer.customize(builder);
                ObjectMapper mapper = builder.build();

                String json = mapper.writeValueAsString(List.of(event));
                List<TrackEvent> restored = mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, TrackEvent.class));
                assertThat(restored).hasSize(1);
                assertion.accept(new Observed(json, restored.getFirst()));
            });
    }

    /**
     * 一次往返的观察结果。
     *
     * @param json 线上 JSON
     * @param round 反序列化回来的事件
     */
    private record Observed(String json, TrackEvent round) {
    }
}
