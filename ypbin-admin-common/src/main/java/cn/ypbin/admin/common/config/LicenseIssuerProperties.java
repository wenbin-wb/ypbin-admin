/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 授权签发密钥配置。
 *
 * <p>本应用作为授权供应方的签发端，持有 SM2 私钥（签名）、SM2 公钥（签发后回验展示）与 SM4 密钥（加密）。
 * 密钥经配置托管、优先由环境变量注入，不落库——即便数据库被拖库也不会泄露签名私钥。首次部署可调用
 * 「生成密钥对」接口产出一套密钥，再写入部署环境。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ypbin.license.issuer")
public class LicenseIssuerProperties {

    /** Base64 SM2 公钥（签发后回验授权、计算展示状态用） */
    private String publicKey;

    /** Base64 SM2 私钥（签名用，务必仅注入到签发端环境） */
    private String privateKey;

    /** Base64 SM4 密钥（加密授权信封用，16 字节） */
    private String sm4Key;
}
