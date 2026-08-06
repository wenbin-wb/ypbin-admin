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

import cn.ypbin.admin.system.model.resp.LicenseRemoteResp;
import cn.ypbin.admin.system.service.SysLicenseService;
import cn.ypbin.starter.core.model.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 联机校验对外接口（消费端专用）。
 *
 * <p>供已部署的消费端联机回验授权状态：按授权编号查询是否已签发且未被吊销。该接口不要求管理员登录，
 * 以共享令牌（请求头 {@code X-License-Token}）轻量鉴权，令牌经部署环境配置（见
 * {@code ypbin.license.remote.token}）。任何判定（令牌无效、授权不存在、非已签发、指纹不符）统一以
 * {@code valid=false} 返回，消费端据此阻断；不抛异常静默放行。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@RestController
@RequestMapping("/open/license")
@RequiredArgsConstructor
public class OpenLicenseController {

    private final SysLicenseService licenseService;

    /**
     * 联机校验单条授权状态。
     *
     * @param licenseId   授权编号（签发时生成，全局唯一）
     * @param fingerprint 消费端机器指纹（可选；授权绑定了指纹时须命中才放行）
     * @param token       共享令牌（请求头 X-License-Token）
     * @return 联机校验结果
     */
    @GetMapping("/verify")
    public R<LicenseRemoteResp> verify(@RequestParam String licenseId,
        @RequestParam(required = false) String fingerprint,
        @RequestHeader(value = "X-License-Token", required = false) String token) {
        return R.ok(licenseService.verifyRemote(licenseId, fingerprint, token));
    }
}
