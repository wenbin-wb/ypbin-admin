/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.modules.system.annotation.PlatformAccess;
import cn.ypbin.admin.modules.system.model.req.PushTestReq;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.messaging.push.PushService;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息推送与站内信接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequiredArgsConstructor
@PlatformAccess
public class PushController {

    private final PushService pushService;

    /**
     * 推送测试（仅 admin 可见的调试端点）。
     */
    @Idempotent
    @Log(value = "推送测试", module = "消息推送")
    @PostMapping("/system/push/test")
    @SaCheckPermission("system:push:test")
    public R<Void> pushTest(@Valid @RequestBody PushTestReq req) {
        pushService.sendToUser(req.getUserId().toString(), "test-message", Map.of("message", req.getMessage()));
        return R.ok();
    }
}
