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

import cn.ypbin.admin.system.service.SysConfigService;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 敏感词词库提供者（starter {@code SensitiveWordProvider} 端口的宿主实现）。
 *
 * <p><strong>词库放哪</strong>：放在系统参数表 {@code sys_config} 的 {@code SENSITIVE_WORDS} 项
 * （逗号分隔的纯文本），不新建词库表。取舍：① 本仓没有敏感词专用表，而 {@code sys_config} 已有
 * 完整的读取/维护/缓存链路（{@link SysConfigService} 本地快照 + 变更后 {@code refreshCache}）
 * 与后台「系统参数」维护界面，复用它零新增 DDL、零新增维护入口；② 不用 {@code sys_dict}：字典的
 * {@code label/value/sort/color} 结构面向枚举展示，词条本身既是值也是展示文本，塞进字典字段语义别扭。
 * 若日后需要「逐词启停/分类」，再迁到字典或专用表更合适。</p>
 *
 * <p><strong>读取时机与代价（重要）</strong>：starter 的 {@code SensitiveWordAutoConfiguration}
 * 在装配 {@code SensitiveWordService} 时<strong>只调用一次</strong> {@link #getWords()} 并用其结果
 * 构建 DFA 词树（{@code SensitiveWordService} 构造器）。因此本实现是「启动期一次性读取」：
 * 运营在后台改词库<strong>不会即时生效</strong>，需重启服务（或由宿主显式调用
 * {@code SensitiveWordService#reload} 重建词树——starter 已提供该入口，本仓当前未接）。
 * 这是本实现明确的取舍：先保证词库有唯一、可维护的来源，热更新作为独立改动评估。</p>
 *
 * <p><strong>空词库不静默</strong>：starter 只在「完全没有 Provider 且配置项为空」时告警；一旦宿主提供了
 * Provider，它就默认宿主已备好词库。因此本类在词库为空时<strong>自己</strong>记 WARN 说明「过滤不会命中
 * 任何词」，避免出现「以为有过滤、实则空转」的静默失效。词库读取失败（如参数表不可用）直接抛错并带堆栈，
 * 不降级成空词库——空词库等于放行全部内容，绝不能由一次异常静默推出。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@Component
@RequiredArgsConstructor
public class DbSensitiveWordProvider implements SensitiveWordProvider {

    private static final Logger log = LoggerFactory.getLogger(DbSensitiveWordProvider.class);

    /** 词库所在的系统参数键（{@code sys_config.config_key}） */
    public static final String CONFIG_KEY = "SENSITIVE_WORDS";

    /** 词条分隔符：英文/中文逗号、英文/中文分号、换行 */
    private static final Pattern SEPARATOR = Pattern.compile("[,，;；\\r\\n]+");

    private final SysConfigService configService;

    @Override
    public Collection<String> getWords() {
        String raw;
        try {
            raw = configService.getString(CONFIG_KEY, "");
        } catch (RuntimeException ex) {
            log.error("读取敏感词词库失败，敏感词过滤将无法工作：configKey={}", CONFIG_KEY, ex);
            throw new IllegalStateException("读取敏感词词库失败：" + CONFIG_KEY, ex);
        }
        List<String> words = parse(raw);
        if (words.isEmpty()) {
            log.warn("系统参数 [{}] 未配置敏感词，@SensitiveWordFilter 不会命中任何词（等同不做过滤）；"
                + "请在「系统参数」中配置，多个词用英文逗号分隔", CONFIG_KEY);
        } else {
            log.info("敏感词词库已加载：configKey={}, 词条数={}（词库在启动期读取，修改后需重启生效）",
                CONFIG_KEY, words.size());
        }
        return words;
    }

    /**
     * 解析逗号/分号/换行分隔的词库文本：去空白、去重、保持配置顺序。
     *
     * @param raw 原始配置值，可为空
     * @return 词条列表（无有效词条时返回空列表）
     */
    private List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> words = new LinkedHashSet<>();
        for (String word : SEPARATOR.split(raw)) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                words.add(trimmed);
            }
        }
        return new ArrayList<>(words);
    }
}
