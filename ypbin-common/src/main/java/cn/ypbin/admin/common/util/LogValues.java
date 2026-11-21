/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.util;

/**
 * 日志值净化：把**外部可控**的字符串收敛成单行可打印文本，再送进日志。
 *
 * <p><b>为什么必须净化</b>（CodeQL {@code java/log-injection} 的判据实质）：URI、文件名、
 * 公告标题这类值来自请求方，若原样拼进日志，攻击者可以用 CR/LF 伪造整条日志行
 * （例如把 {@code "uri=/x\r\n2026-01-01 ERROR 登录成功 user=admin"} 写进去），
 * 让运维与审计看到假记录。脱敏（{@code maskPhone}/{@code maskEmail}）解决的是「别泄露」，
 * 净化解决的是「别伪造」，两件事都需要。</p>
 *
 * <p><b>口径</b>：CR、LF、TAB 及其它控制字符一律替换成 {@code ' '}（不是删除——删除会让
 * {@code a\r\nb} 变成看起来正常的 {@code ab}，反而更像合法值），并截断到 {@link #MAX_LENGTH}。</p>
 *
 * @author wenbin
 * @since 2026-11-22
 */
public final class LogValues {

    /** 单字段日志值上限：超长值会把日志行撑爆，也会给日志注入留出更大的拼接空间。 */
    public static final int MAX_LENGTH = 256;

    private LogValues() {
    }

    /**
     * 净化单个日志字段值。
     *
     * @param raw 原始值（可能为 {@code null}，可能含 CR/LF/控制字符）
     * @return 单行文本；{@code null} 原样返回 {@code null}（保持「缺失」与「空串」的区别）
     */
    public static String oneLine(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString();
        StringBuilder out = new StringBuilder(Math.min(text.length(), MAX_LENGTH) + 8);
        for (int i = 0; i < text.length() && out.length() < MAX_LENGTH; i++) {
            char ch = text.charAt(i);
            // Character.isISOControl 覆盖 CR(13)/LF(10)/TAB(9)/NUL(0) 等全部控制字符
            out.append(Character.isISOControl(ch) ? ' ' : ch);
        }
        if (text.length() > MAX_LENGTH) {
            out.append("…(truncated)");
        }
        return out.toString();
    }
}
