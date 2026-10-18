/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * {@code sys_config} 敏感值脱敏的<b>唯一判据与唯一形态</b>（内部端点与列表页共用）。
 *
 * <p><strong>为什么必须收成一处</strong>：此前内部端点（{@code SystemClientImpl}）用 6 个<b>后缀</b>
 * 判据、打码成 {@code ****末4位}；列表页（{@code SysConfigServiceImpl}）却用 2 个<b>精确键</b> +
 * {@code SOCIAL_*_CLIENT_SECRET} 前缀规则、把值置空 {@code ""}。两套判据并存导致
 * <strong>「internal 打码、列表页返回明文」的泄露面</strong>：凡命中后缀规则但不属那 2 个精确键的配置项
 * （如管理员自行新增的 {@code *PROVIDER_API_KEY}），管理面列表会把密钥原文回显给前端。判据与形态各自
 * 收成一处后，两侧对同一个键必然给出同一个答案。</p>
 *
 * <p><strong>判据选择：后缀集合</strong>（{@link #SENSITIVE_SUFFIXES}）。选择理由：</p>
 * <ol>
 *   <li><b>与已上线行为对齐</b>：内部端点原本就用这套后缀判据（只放宽、不收紧），切换后不会有配置项从
 *       「打码」变成「明文」；</li>
 *   <li><b>覆盖新增键</b>：精确键判据对新增的密钥类配置项完全失效（这正是本类要消灭的泄露面），
 *       后缀判据对 {@code *}_SECRET/{@code *}_PASSWORD/{@code *}_TOKEN/{@code *}_API_KEY 等命名自动生效；</li>
 *   <li><b>误报可接受</b>：万一某个非敏感键以这些后缀结尾，代价只是「管理面看不到原文、需重新填一次」，
 *       而漏判的代价是密钥明文出网——两者不对称，故取保守侧。</li>
 * </ol>
 *
 * <p><strong>形态选择：{@code ****末4位}</strong>（长度 ≤ 4 时整体打码为 {@code ****}）。选择理由：</p>
 * <ol>
 *   <li><b>比置空更多信息、比明文少泄露</b>：末 4 位足以让管理员<em>确认</em>「配的是哪一把密钥」，
 *       又不足以还原；置空则连「是否已配置」都看不出来；</li>
 *   <li><b>与内部端点既有形态一致</b>：auth 等下游经 internal 读到的一直是这个形态，改成置空会让
 *       已有调用方的判空逻辑（{@code AuthConfigReader#getString} 用 {@code null}/空串判断缺失）出现
 *       语义漂移；</li>
 *   <li><b>可被写路径识别</b>：管理面把掩码值原样提交回来时，{@link #isMaskedValue} 能让写路径把它
 *       当成「未修改」跳过，避免把 {@code ****abcd} 当成真实密钥写库。</li>
 * </ol>
 *
 * <p><strong>已知边界</strong>：真实值若恰好以 4 个星号开头（如 {@code ****real}），会被写路径误判为
 * 「未修改」。这种密钥值在实践中不存在（各云厂商密钥均为字母数字），且误判方向是「保留旧值」而非
 * 「把掩码写库」，不会造成泄露或不可逆损坏。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class ConfigValueMasker {

    /** 敏感键后缀集合：键名（忽略大小写）以任一后缀结尾即视为敏感 */
    private static final List<String> SENSITIVE_SUFFIXES = List.of(
        "_SECRET", "_PASSWORD", "_TOKEN", "_ACCESS_KEY", "_PRIVATE_KEY", "_API_KEY");

    /** 打码前缀（固定 4 个星号，长度与位数无关，避免从长度反推密钥长度） */
    private static final String MASK_PREFIX = "****";

    /** 掩码值形态：{@code ****} 开头即视为「已脱敏的值」 */
    private static final Pattern MASKED_VALUE = Pattern.compile("^\\*{4}.*$");

    private ConfigValueMasker() {
    }

    /**
     * 判定配置键是否敏感（键名忽略大小写，按后缀匹配）。
     *
     * @param configKey 配置键，允许为 {@code null}
     * @return 敏感返回 {@code true}
     */
    public static boolean isSensitive(String configKey) {
        if (configKey == null) {
            return false;
        }
        String upperKey = configKey.toUpperCase(Locale.ROOT);
        return SENSITIVE_SUFFIXES.stream().anyMatch(upperKey::endsWith);
    }

    /**
     * 按统一口径脱敏：敏感键保留末 4 位、其余打码；非敏感键原样返回。
     *
     * @param configKey 配置键
     * @param rawValue  原始值，允许为 {@code null}
     * @return 脱敏后的值；非敏感键或空值原样返回
     */
    public static String mask(String configKey, String rawValue) {
        if (rawValue == null || rawValue.isBlank() || !isSensitive(configKey)) {
            return rawValue;
        }
        return rawValue.length() <= MASK_PREFIX.length() ? MASK_PREFIX
            : MASK_PREFIX + rawValue.substring(rawValue.length() - MASK_PREFIX.length());
    }

    /**
     * 判定一个提交值是否为本类产出的掩码（写路径据此识别「未修改」）。
     *
     * @param value 待判定的值，允许为 {@code null}
     * @return 形如 {@code ****xxx} 返回 {@code true}
     */
    public static boolean isMaskedValue(String value) {
        return value != null && MASKED_VALUE.matcher(value).matches();
    }
}
