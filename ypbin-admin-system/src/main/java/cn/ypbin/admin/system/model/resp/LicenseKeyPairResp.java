/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新生成的签发密钥对响应。
 *
 * <p>供首次部署时生成一套签发密钥，写入部署环境的 {@code ypbin.license.issuer.*}（或对应环境变量）后生效。
 * 私钥仅在生成时返回一次，服务端不落库，请妥善离线保管。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LicenseKeyPairResp {

    /** Base64 SM2 公钥 */
    private String publicKey;

    /** Base64 SM2 私钥（仅此次返回，务必离线保管） */
    private String privateKey;

    /** Base64 SM4 密钥 */
    private String sm4Key;
}
