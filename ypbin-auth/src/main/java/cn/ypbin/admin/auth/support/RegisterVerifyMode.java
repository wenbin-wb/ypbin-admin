/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.auth.support;

import cn.ypbin.starter.core.exception.BusinessException;
import java.util.Locale;

/**
 * 注册归属验证方式（系统参数 {@code REGISTER_VERIFY_MODE}）。
 *
 * <p><b>未知取值必须拒绝</b>：配置写错时若静默当作 {@link #NONE}，会让本应强制的验证
 * 悄悄失效——这是安全开关，不能有「解析失败就放行」的分支。</p>
 *
 * @author wenbin
 * @since 2026-11-10
 */
public enum RegisterVerifyMode {

    /** 不验证（默认；仍受行为验证码与限流保护） */
    NONE,

    /** 手机短信验证码 */
    SMS,

    /** 邮箱验证码 */
    EMAIL;

    /**
     * 解析配置值（忽略大小写与首尾空白）。
     *
     * @param raw 原始配置值，可空
     * @return 验证方式
     */
    public static RegisterVerifyMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                "系统参数 REGISTER_VERIFY_MODE 取值非法，仅支持 NONE/SMS/EMAIL：" + raw.trim());
        }
    }
}
