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
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.messaging.push.PushService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息推送与站内信接口。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@RestController
@RequiredArgsConstructor
public class PushController extends BaseController {

    private final PushService pushService;

    /**
     * 推送测试（仅 admin 可见的调试端点）。
     */
    @PostMapping("/system/push/test")
    public R<Void> pushTest(@RequestParam Long userId, @RequestBody Map<String, Object> payload) {
        pushService.sendToUser(userId.toString(), "test-message", payload);
        return ok();
    }
}
