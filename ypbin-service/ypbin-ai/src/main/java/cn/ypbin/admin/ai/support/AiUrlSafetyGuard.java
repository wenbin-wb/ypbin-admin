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
package cn.ypbin.admin.ai.support;

import cn.ypbin.starter.core.exception.BusinessException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.stereotype.Component;

/**
 * 服务端发起 HTTP 请求前的统一 SSRF 守卫。
 *
 * <p><strong>为什么必须只有一份</strong>：知识库 URL 导入与模型「测试连接」都会让服务端
 * 替用户发请求。两处各写一套判定时，很容易一处放行了环回地址、另一处放行了元数据网段
 * （{@code 169.254.169.254} / {@code 100.100.100.200}）——这正是本次审查发现的口径分裂。
 * 本类把「协议白名单 + 用户信息禁止 + 解析地址非内网/保留网段 + 禁止跟随重定向」收敛为唯一实现。</p>
 *
 * <p><strong>残留风险（已知边界）</strong>：DNS 在「校验解析」与「实际连接」之间可能被再次解析
 * （DNS rebinding）。域名只要任一解析结果命中黑名单即整体拒绝，已把风险压到最小；彻底根治需要
 * 解析与连接使用同一个已校验 IP，属连接层改造，不在本类范围。</p>
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
     * 仅放行「公网可路由地址」。委托 Spring Boot 维护的特殊用途网段清单
     * （含 CGNAT {@code 100.64.0.0/10}），比自己枚举更完整且随版本更新。
     */
    private static final InetAddressFilter EXTERNAL_ADDRESS_FILTER = InetAddressFilter.externalAddresses();

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
                if (!EXTERNAL_ADDRESS_FILTER.matches(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            // 解析失败即拒绝（fail-closed），不能把请求发给无法校验归属的目标
            throw new BusinessException(scene + "域名解析失败：" + host);
        }
    }
}
