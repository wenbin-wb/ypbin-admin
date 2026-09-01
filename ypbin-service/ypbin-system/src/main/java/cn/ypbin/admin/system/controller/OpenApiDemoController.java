/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.controller;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.sign.annotation.ApiSign;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API 示例接口。标注 {@code @ApiSign} 的接口需通过签名校验（AK/SK + timestamp + nonce + sign）。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequestMapping("/open-api")
public class OpenApiDemoController {

    @ApiSign
    @PostMapping("/demo")
    public R<Map<String, Object>> demo(@RequestBody Map<String, Object> params) {
        return R.ok(Map.of("echo", params, "message", "开放 API 签名校验通过"));
    }
}
