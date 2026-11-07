/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.social;

import cn.ypbin.starter.core.exception.BusinessException;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * 第三方登录配置的键规约与解析（单一来源）。
 *
 * <p>{@code SocialConfigServiceImpl}（管理面读写）与 {@link SocialConfigReader}（内部 Feign 契约）
 * 此前各自维护一份 {@code SOCIAL_*} 后缀与解析函数，已经出现「读取侧接受 {@code 1}、管理侧只认
 * true/false」这类漂移。这里只保留一份常量与纯函数，两侧都调用它。</p>
 *
 * @author wenbin
 * @since 2026-10-27
 */
public final class SocialConfigKeys {

    /** 平台配置分组 */
    public static final String GROUP = "social";

    /** 配置键前缀 */
    private static final String PREFIX = "SOCIAL_";

    /** 启用后缀 */
    public static final String ENABLED_SUFFIX = "_ENABLED";

    /** ClientId 后缀 */
    public static final String CLIENT_ID_SUFFIX = "_CLIENT_ID";

    /** ClientSecret 后缀 */
    public static final String CLIENT_SECRET_SUFFIX = "_CLIENT_SECRET";

    /** 回调地址后缀 */
    public static final String REDIRECT_URI_SUFFIX = "_REDIRECT_URI";

    /** 公钥后缀（支付宝专用） */
    public static final String PUBLIC_KEY_SUFFIX = "_PUBLIC_KEY";

    /** 支付宝平台标识（需公钥配置的平台） */
    public static final String ALIPAY_SOURCE = "alipay";

    private SocialConfigKeys() {
    }

    /**
     * 归一平台标识（去空白 + 小写）。
     *
     * @param source 原始平台标识
     * @return 归一后的标识
     */
    public static String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            throw new BusinessException("第三方登录平台不能为空");
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 平台配置键前缀。
     *
     * @param source 已归一的平台标识
     * @return 如 {@code SOCIAL_GITHUB_}
     */
    public static String keyPrefix(String source) {
        return PREFIX + source.toUpperCase(Locale.ROOT);
    }

    /**
     * 某平台的完整配置键集合（支付宝多一个公钥键）。
     *
     * @param source 已归一的平台标识
     * @return 键集合
     */
    public static Set<String> platformKeys(String source) {
        String prefix = keyPrefix(source);
        if (ALIPAY_SOURCE.equals(source)) {
            return Set.of(prefix + ENABLED_SUFFIX, prefix + CLIENT_ID_SUFFIX,
                prefix + CLIENT_SECRET_SUFFIX, prefix + REDIRECT_URI_SUFFIX,
                prefix + PUBLIC_KEY_SUFFIX);
        }
        return Set.of(prefix + ENABLED_SUFFIX, prefix + CLIENT_ID_SUFFIX,
            prefix + CLIENT_SECRET_SUFFIX, prefix + REDIRECT_URI_SUFFIX);
    }

    /**
     * 解析库内已存的启用开关（<b>读路径唯一解析口</b>）。
     *
     * <p>管理面写路径只会写 {@code true}/{@code false}；历史数据里存在 {@code 1}/{@code 0}
     * （早期读取侧的宽松格式），读路径必须继续兼容——否则「列表里选得中、读取时却报错」，
     * 该平台的启停与密钥轮换会全部失效。除此以外的取值不是某种假值而是数据损坏，
     * 显式失败而不是静默按停用处理（静默会把「配置写错了」伪装成「平台没启用」）。</p>
     *
     * <p>选源（{@code SocialConfigReader#enabledSources}）与读取必须共用本方法：
     * 两处各用一套解析，就是「先按宽松规则选中、再按严格规则读崩」的成因。</p>
     *
     * @param enabled 配置值
     * @param source  平台标识（用于报错）
     * @return 是否启用
     */
    public static boolean parseStoredEnabled(String enabled, String source) {
        // 库值可能带首尾空白（历史手工写入），解析前统一归一，避免「同值不同判」
        String normalized = enabled == null ? "" : enabled.trim();
        if ("true".equalsIgnoreCase(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized) || "0".equals(normalized)) {
            return false;
        }
        throw new BusinessException("第三方登录启用配置必须为 true/false（历史值 1/0 仍兼容）：" + source);
    }

    /**
     * 从 {@code SOCIAL_XXX_ENABLED} 反推平台标识。
     *
     * @param configKey 配置键
     * @return 平台标识；无法识别时返回空串
     */
    public static String sourceOf(String configKey) {
        if (configKey == null || !configKey.startsWith(PREFIX)
            || !configKey.endsWith(ENABLED_SUFFIX)) {
            return "";
        }
        String body = configKey.substring(PREFIX.length(),
            configKey.length() - ENABLED_SUFFIX.length());
        return body.toLowerCase(Locale.ROOT);
    }

    /**
     * 去空白，null 归一为空串。
     *
     * @param value 原值
     * @return 去空白后的值
     */
    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
