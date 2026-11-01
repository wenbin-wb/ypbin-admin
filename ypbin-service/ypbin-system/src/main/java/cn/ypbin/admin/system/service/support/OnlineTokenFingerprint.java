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
package cn.ypbin.admin.system.service.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 在线会话令牌指纹（SHA-256 十六进制）。
 *
 * <p><strong>为什么不能把 token 原文放进列表响应</strong>：starter {@code OnlineUser.token} 就是
 * Sa-Token 的 Bearer Token，拿到即可冒充该用户。在线用户列表只需要一个「可用于踢人」的稳定句柄，
 * 故对外只暴露不可逆指纹：列表返回指纹，踢人时按指纹在当前会话集合中反查 token 再踢。</p>
 *
 * <p>指纹不加盐：token 本身是足够长的高熵随机串，且这里只需要相等比较、不需要抗彩虹表。</p>
 *
 * @author wenbin
 * @since 2026-11-01
 */
public final class OnlineTokenFingerprint {

    private OnlineTokenFingerprint() {
    }

    /**
     * 计算令牌指纹。
     *
     * @param token 会话令牌
     * @return 小写十六进制 SHA-256；令牌为空时返回 {@code null}
     */
    public static String hash(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JVM 必须支持 SHA-256；缺失属运行环境损坏，不能让列表静默返回空指纹
            throw new IllegalStateException("JVM 不支持 SHA-256，无法生成会话指纹", e);
        }
    }
}
