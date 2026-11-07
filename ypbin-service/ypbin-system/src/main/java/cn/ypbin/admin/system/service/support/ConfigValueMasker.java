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
 * <h2>判据：能「单独生效」才是敏感</h2>
 *
 * <p>判据本质是<b>「这个值泄露后，攻击者能否<em>单独</em>完成认证/解密/调用」</b>，而不是
 * 「名字里有没有 KEY」。据此分两类：</p>
 *
 * <ul>
 *   <li><b>敏感（打码）</b>：凭据类后缀 {@link #SENSITIVE_SUFFIXES} —— 密钥、口令、令牌、凭证。
 *       其中 {@code _KEY} 是最关键的一条：它同时覆盖 {@code _API_KEY}、{@code _ACCESS_KEY}、
 *       {@code _PRIVATE_KEY}、{@code _SECRET_KEY} 以及厂商自定义命名（如
 *       {@code ALIYUN_SECRET_KEY}、{@code MY_SECRET_KEY}）。此前只列 4 个以 {@code _KEY} 结尾的
 *       复合后缀，导致 {@code ALIYUN_SECRET_KEY} 这类键<b>判为非敏感、两侧明文回显</b>。</li>
 *   <li><b>非敏感（明文）</b>：<b>标识符</b>类键 —— 典型是 {@code SMS_ACCESS_KEY_ID}（AccessKeyId）。
 *       它是「用户名」性质的标识，<em>单独不能通过认证</em>，必须与 Secret 配对使用；打码会让运维
 *       无法确认「当前配的是哪一把/哪个账号」，排障成本实打实。同理 {@code MAIL_USERNAME}（邮箱账号）、
 *       {@code *_CLIENT_ID}（OAuth 应用标识）、{@code MAIL_FROM} / {@code *_REDIRECT_URI} /
 *       {@code *_ENABLED} 等配置项一律明文。这类键<b>不会命中</b> {@link #SENSITIVE_SUFFIXES}，
 *       故不设例外清单（无魔法白名单，判据本身就是「后缀是否属凭据类」）。</li>
 * </ul>
 *
 * <p><strong>已核对的种子数据（deploy/sql/002-data.sql）误伤面</strong>：新判据下会被打码的键只有
 * {@code MAIL_PASSWORD}、{@code SMS_ACCESS_KEY_SECRET}、{@code SOCIAL_*_CLIENT_SECRET}（6 个平台）与
 * {@code SOCIAL_ALIPAY_PUBLIC_KEY}；{@code SMS_ACCESS_KEY_ID}、{@code MAIL_USERNAME}、
 * {@code MAIL_FROM}、{@code SOCIAL_*_CLIENT_ID}、{@code *_REDIRECT_URI}、{@code *_ENABLED}、
 * {@code PASSWORD_*} 的阈值类键全部保持明文 —— 均符合上面的判据。</p>
 *
 * <p><strong>已知误报（保守侧，如实登记）</strong>：{@code *_PUBLIC_KEY}（如
 * {@code SOCIAL_ALIPAY_PUBLIC_KEY}）会被 {@code _KEY} 后缀命中而打码。严格按「能否单独生效」判据，
 * 公钥不是凭据（不能单独完成认证），本不该打码。之所以接受该误报：① 判据保持「凭据类后缀」这一条
 * 简单规则，避免为公钥开一个语义例外（例外清单是新的漂移点）；② 影响面可控 —— 该键的读路径走
 * {@code SocialConfigReader}/{@code SocialConfigServiceImpl}（直读库、不经本脱敏），第三方登录不受影响；
 * 仅管理面列表页与内部端点看到的是掩码。若将来确需在管理面确认公钥内容，再显式引入「公钥类键明文」
 * 的例外并写明理由。</p>
 *
 * <h2>形态：{@code ****末4位}</h2>
 *
 * <p>（长度 ≤ 4 时整体打码为 {@code ****}。）选择理由：</p>
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
 * <p><strong>写路径的两道守卫</strong>（缺任一条都会引入新的静默损坏）：</p>
 * <ol>
 *   <li>{@link #isMaskedValue} 先 {@code trim()} 再判定 —— 否则提交 {@code " ****0001"} 这种
 *       前导空格变体就能绕过「未修改」识别，把掩码当真实值写库；</li>
 *   <li>「掩码 = 未修改」<b>只对敏感键成立</b>：写路径必须先问 {@link #isSensitive}。否则给
 *       <b>非敏感键</b>填一个恰好以 4 个星号开头的真实值（如 {@code ****brand-new-name}）会被静默判为
 *       「未修改」、实际不更新 —— 用户改了值却没生效，且没有任何报错。</li>
 * </ol>
 *
 * <p><strong>其他已知边界</strong>：真实值若恰好以 4 个星号开头（如 {@code ****real}）且键敏感，
 * 会被写路径误判为「未修改」。这种密钥值在实践中不存在（各云厂商密钥均为字母数字），且误判方向是
 * 「保留旧值」而非「把掩码写库」，不会造成泄露或不可逆损坏。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class ConfigValueMasker {

    /**
     * 敏感键后缀集合：键名（忽略大小写）以任一后缀结尾即视为敏感。
     *
     * <p>覆盖凭据类命名：{@code _SECRET}（含 {@code _CLIENT_SECRET}、{@code _SECRET_KEY}）、
     * {@code _PASSWORD}/{@code _PASSWD}/{@code _PWD}（同一语义的三种常见拼法）、
     * {@code _TOKEN}（含 {@code _ACCESS_TOKEN}）、{@code _KEY}（含
     * {@code _API_KEY}/{@code _ACCESS_KEY}/{@code _PRIVATE_KEY}/{@code _SECRET_KEY}，
     * 以及 {@code ALIYUN_SECRET_KEY} 这类厂商命名）、{@code _CREDENTIAL}/{@code _CREDENTIALS}。</p>
     *
     * <p>刻意<b>不</b>包含 {@code _ID}：{@code *_KEY_ID}（AccessKeyId）与 {@code *_CLIENT_ID}
     * 都是标识符，单独不能通过认证，打码反而妨碍运维确认配置（详见类注释的判据说明）。</p>
     */
    private static final List<String> SENSITIVE_SUFFIXES = List.of(
        "_SECRET", "_PASSWORD", "_PASSWD", "_PWD", "_TOKEN", "_KEY", "_CREDENTIAL", "_CREDENTIALS");

    /** 打码前缀（固定 4 个星号，长度与位数无关，避免从长度反推密钥长度） */
    private static final String MASK_PREFIX = "****";

    /** 掩码值形态：{@code ****} 开头即视为「已脱敏的值」（判定前先 trim，防前导空格绕过） */
    private static final Pattern MASKED_VALUE = Pattern.compile("^\\*{4}.*$");

    private ConfigValueMasker() {
    }

    /**
     * 判定配置键是否敏感（键名忽略大小写，按凭据类后缀匹配）。
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
     * <p><b>先 trim 再判定</b>：{@code " ****0001"} 这类前导空格变体若不被识别，就会被当成真实值写库，
     * 把密钥静默覆盖成掩码。trim 后判定不改变「真实密钥不会被误判」的性质（真实密钥不以 4 个星号开头）。</p>
     *
     * <p><b>调用方必须先确认键敏感</b>：本方法只看值的形状，不看键。非敏感键填一个以 {@code ****} 开头的
     * 真实值完全合法，写路径若不加 {@link #isSensitive} 守卫，会把用户的真实修改静默丢掉。</p>
     *
     * @param value 待判定的值，允许为 {@code null}
     * @return trim 后形如 {@code ****xxx} 返回 {@code true}
     */
    public static boolean isMaskedValue(String value) {
        return value != null && MASKED_VALUE.matcher(value.trim()).matches();
    }
}
