/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 敏感词词库提供者测试。
 *
 * <p>覆盖三件事：① 词库文本的解析口径（分隔符/去空白/去重/保序）；② 空词库返回空集合且不抛错；
 * ③ 读取失败<strong>不降级成空词库</strong>（空词库等于放行全部内容），而是带原因抛错。
 * 另外用真实 starter {@code SensitiveWordService} 验证「配了词就命中、没配的词不命中」。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class DbSensitiveWordProviderTest {

    private final SysConfigService configService = mock(SysConfigService.class);

    private final DbSensitiveWordProvider provider = new DbSensitiveWordProvider(configService);

    @Test
    @DisplayName("解析词库：支持中英文逗号/分号/换行分隔，去空白、去重、保持配置顺序")
    void shouldParseWordsFromConfigValue() {
        when(configService.getString(DbSensitiveWordProvider.CONFIG_KEY, ""))
            .thenReturn(" 违规词 ,禁止词，\n  敏感词 ;\t违规词\r\n ");

        assertThat(provider.getWords()).containsExactly("违规词", "禁止词", "敏感词");
    }

    @Test
    @DisplayName("未配置或全空白时返回空集合（不返回 null）")
    void shouldReturnEmptyListWhenNotConfigured() {
        when(configService.getString(DbSensitiveWordProvider.CONFIG_KEY, "")).thenReturn("");
        assertThat(provider.getWords()).isEmpty();

        when(configService.getString(DbSensitiveWordProvider.CONFIG_KEY, "")).thenReturn("  ,  \n ; ");
        assertThat(provider.getWords()).isEmpty();
    }

    @Test
    @DisplayName("读取词库失败时带原因抛错，绝不降级为空词库")
    void shouldFailFastWhenConfigUnreadable() {
        IllegalStateException cause = new IllegalStateException("参数表不可用");
        when(configService.getString(DbSensitiveWordProvider.CONFIG_KEY, "")).thenThrow(cause);

        assertThatThrownBy(provider::getWords)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(DbSensitiveWordProvider.CONFIG_KEY)
            .hasRootCause(cause);
    }

    @Test
    @DisplayName("词库确实驱动命中/未命中判定（用真实 DFA 服务验证）")
    void shouldFilterConfiguredWordsOnly() {
        when(configService.getString(DbSensitiveWordProvider.CONFIG_KEY, ""))
            .thenReturn("违规词,禁止词");

        SensitiveWordService sensitiveWordService = new SensitiveWordService(provider.getWords());

        assertThat(sensitiveWordService.contains("公告里出现了违规词")).isTrue();
        assertThat(sensitiveWordService.contains("这里只有禁止词")).isTrue();
        assertThat(sensitiveWordService.contains("完全正常的公告内容")).isFalse();
        assertThat(sensitiveWordService.filter("违规词与正常内容", '*')).isEqualTo("***与正常内容");
    }
}
