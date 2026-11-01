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
package cn.ypbin.admin.modules.ai.support;

import cn.ypbin.starter.core.exception.BusinessException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * 服务端发起 HTTP 请求前的统一 SSRF 守卫。
 *
 * <p>知识库 URL / Sitemap / RSS 导入与模型「测试连接」都会让服务端替用户发请求。
 * 本类把「协议白名单 + 用户信息禁止 + 解析地址非内网/保留网段 + 禁止跟随重定向」收敛为唯一实现，
 * 防止一处放行环回地址、另一处放行云元数据网段（{@code 169.254.169.254} / {@code 100.100.100.200}）。</p>
 *
 * <p><strong>残留风险（已知边界）</strong>：DNS 在「校验解析」与「实际连接」之间可能被再次解析
 * （DNS rebinding）。域名只要任一解析结果命中黑名单即整体拒绝，已把风险压到最小。</p>
 *
 * @author wenbin
 * @since 2026-11-01
 */
@Component
public class AiUrlSafetyGuard {

    /** URL 长度上限 */
    public static final int MAX_URL_LENGTH = 2048;

    private static final String SCHEME_HTTP = "http";

    private static final String SCHEME_HTTPS = "https";

    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ypbin-knowledge-bot/1.0)";

    private static final int FETCH_TIMEOUT_MS = 15_000;

    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;


    /**
     * 校验 URL 是否可以由服务端发起请求。
     *
     * @param rawUrl 待校验 URL
     * @param scene  场景名（用于错误提示，如「导入地址」「模型接口地址」）
     */
    public void requireSafeUrl(String rawUrl, String scene) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException(scene + "不能为空");
        }
        if (rawUrl.length() > MAX_URL_LENGTH) {
            throw new BusinessException(scene + "过长，已拒绝");
        }
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new BusinessException(scene + "格式不合法：" + rawUrl);
        }
        String scheme = uri.getScheme();
        if (scheme == null
            || !(SCHEME_HTTP.equalsIgnoreCase(scheme) || SCHEME_HTTPS.equalsIgnoreCase(scheme))) {
            throw new BusinessException("仅支持 http/https 协议的" + scene);
        }
        if (uri.getUserInfo() != null) {
            throw new BusinessException(scene + "不允许包含用户信息");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(scene + "缺少主机名：" + rawUrl);
        }
        if (isBlockedHost(host, scene)) {
            throw new BusinessException(scene + "指向内网或保留网段，已拒绝：" + rawUrl);
        }
    }

    /**
     * 校验后创建 Jsoup 连接（禁止跟随重定向，限制超时与响应体大小）。
     *
     * @param url   目标地址
     * @param scene 场景名（用于错误提示）
     * @return 已配置的连接
     */
    public Connection guardedConnection(String url, String scene) {
        requireSafeUrl(url, scene);
        return Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(FETCH_TIMEOUT_MS)
            .maxBodySize(MAX_BODY_BYTES)
            .followRedirects(false);
    }

    private static boolean isBlockedHost(String host, String scene) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            // 解析失败即拒绝（fail-closed），不能把请求发给无法校验归属的目标
            throw new BusinessException(scene + "域名解析失败：" + host);
        }
    }

    /**
     * 是否为不可访问的地址（环回、私网、链路本地、CGNAT、组播、保留网段等）。
     *
     * <p>boot 线的 Spring Boot 4.1.0 尚未提供 {@code InetAddressFilter}，故在此显式判定；
     * 覆盖 JDK 的 any/loopback/link-local/site-local/multicast 判断，并补充 CGNAT
     * {@code 100.64.0.0/10}（云厂商元数据服务段如 100.100.100.200）、文档/基准测试网段、
     * {@code 240.0.0.0/4} 保留段与 IPv6 唯一本地地址。</p>
     *
     * @param address 待判定地址
     * @return 不可访问返回 {@code true}
     */
    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            // CGNAT 100.64.0.0/10
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            // 0.0.0.0/8
            if (first == 0) {
                return true;
            }
            // 192.0.2.0/24、198.51.100.0/24、203.0.113.0/24（文档）、198.18.0.0/15（基准测试）
            if (first == 192 && second == 0 && third == 2) {
                return true;
            }
            if (first == 198 && (second == 51 || second == 18)) {
                return true;
            }
            if (first == 203 && second == 0 && third == 113) {
                return true;
            }
            // 240.0.0.0/4 保留
            return first >= 240;
        }
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            // fc00::/7 唯一本地地址
            if ((first & 0xfe) == 0xfc) {
                return true;
            }
            // IPv4-mapped ::ffff:a.b.c.d：按 IPv4 规则判定
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
                try {
                    return isBlockedAddress(InetAddress.getByAddress(
                        Arrays.copyOfRange(bytes, 12, 16)));
                } catch (UnknownHostException e) {
                    return true;
                }
            }
        }
        return false;
    }
}
