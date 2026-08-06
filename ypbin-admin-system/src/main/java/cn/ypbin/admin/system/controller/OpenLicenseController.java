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
import cn.ypbin.starter.sign.core.SignChecker;
import cn.ypbin.starter.sign.core.SignResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 联机校验对外接口（消费端专用）。
 *
 * <p>供已部署的消费端联机回验授权状态：按授权编号查询是否已签发且未被吊销。该接口不要求管理员登录，
 * 以开放应用 AK/SK 接口签名鉴权（accessKey/timestamp/nonce/sign 四件套，本控制器内手工经
 * {@code SignChecker} 校验）：鉴权失败与任何业务判定（授权不存在、非已签发、指纹不符）统一以
 * {@code valid=false} 返回，消费端据此阻断；不抛异常、不返回无 {@code data} 的错误体静默放行。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@RestController
@RequestMapping("/open/license")
@RequiredArgsConstructor
public class OpenLicenseController {

    private final SysLicenseService licenseService;
    private final SignChecker signChecker;

    /**
     * 联机校验单条授权状态。
     *
     * @param licenseId   授权编号（签发时生成，全局唯一）
     * @param fingerprint 消费端机器指纹（可选；授权绑定了指纹时须命中才放行）
     * @param request     HTTP 请求（携带开放应用签名四件套）
     * @return 联机校验结果
     */
    @GetMapping("/verify")
    public R<LicenseRemoteResp> verify(@RequestParam String licenseId,
        @RequestParam(required = false) String fingerprint,
        HttpServletRequest request) {
        SignResult sign = signChecker.check(request);
        if (!sign.success()) {
            // 签名鉴权失败必须返回 valid=false 而非依赖拦截器抛 R.fail：消费端联机校验对非 200/无 data 的
            // 响应一律「放行+告警」，只有明确 valid=false 才阻断；走拦截器会把鉴权失败静默放行、形同虚设
            return R.ok(LicenseRemoteResp.invalid("联机校验鉴权失败，签名无效或已过期"));
        }
        return R.ok(licenseService.verifyRemote(licenseId, fingerprint));
    }
}
