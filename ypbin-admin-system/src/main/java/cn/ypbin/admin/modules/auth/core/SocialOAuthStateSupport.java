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
package cn.ypbin.admin.modules.auth.core;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.web.util.WebRequestUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OAuth {@code state} 的签发与校验（防登录 CSRF）。
 *
 * <p><strong>为什么必须做</strong>：JustAuth 的 {@code AuthStateUtils.createState()} 只是生成随机串，
 * 没有任何存储与比对。攻击者可用自己的第三方账号拿到授权码，拼成回调链接诱导受害者打开，
 * 使受害者的会话建立在攻击者账号上（或反向把攻击者账号绑到受害者账号）。仅“校验 state 存在”
 * 不够——攻击者同样能先调授权接口拿到一个合法 state，因此 state 必须与<strong>发起授权的浏览器</strong>
 * 绑定。</p>
 *
 * <p><strong>绑定方式</strong>：签发时把 state 同时写入短生命周期 HttpOnly Cookie（{@code SameSite=Lax}，
 * 仅 Path=/auth/social），回调时要求请求参数中的 state 与该 Cookie 逐字节相等，并立即清除 Cookie。
 * Cookie 是浏览器维度、攻击者无法替受害者写入，因此攻击者手里的 state 在受害者浏览器上比对失败。
 * 若前端跨源部署，需改为 {@code SameSite=None; Secure} 并开启跨源凭据。</p>
 *
 * @author wenbin
 * @since 2026-11-01
 */
@Component
public class SocialOAuthStateSupport {

    /** 每个平台一个 Cookie，避免多标签页同时授权不同平台时互相覆盖 */
    private static final String COOKIE_PREFIX = "social_state_";

    /** state 有效期：足够完成第三方授权，又限制被截获后的可用窗口 */
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private static final int STATE_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成 state 并写入浏览器 Cookie。
     *
     * @param source   平台标识
     * @param response HTTP 响应
     * @return 随授权 URL 下发的 state
     */
    public String issue(String source, HttpServletResponse response) {
        byte[] bytes = new byte[STATE_BYTES];
        RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        response.addHeader(HttpHeaders.SET_COOKIE,
            cookie(source, state).maxAge(STATE_TTL.getSeconds()).build().toString());
        return state;
    }

    /**
     * 校验回调携带的 state 与浏览器 Cookie 是否一致；无论成败都清除 Cookie（一次性）。
     *
     * @param source   平台标识
     * @param state    回调参数中的 state
     * @param response HTTP 响应（用于清除 Cookie）
     */
    public void verify(String source, String state, HttpServletResponse response) {
        String expected = readCookie(source);
        // 先清除再判断：同一 state 不允许重放
        response.addHeader(HttpHeaders.SET_COOKIE,
            cookie(source, "").maxAge(0).build().toString());
        if (!StringUtils.hasText(state) || !StringUtils.hasText(expected)
            || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                state.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("授权状态校验失败，请重新发起第三方登录");
        }
    }

    private String readCookie(String source) {
        HttpServletRequest request = WebRequestUtils.request();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = cookieName(source);
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String source, String value) {
        return ResponseCookie.from(cookieName(source), value)
            .path("/auth/social")
            .httpOnly(true)
            .sameSite("Lax")
            .secure(WebRequestUtils.request().isSecure());
    }

    private static String cookieName(String source) {
        String normalized = source == null ? ""
            : source.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
        return COOKIE_PREFIX + normalized;
    }
}
