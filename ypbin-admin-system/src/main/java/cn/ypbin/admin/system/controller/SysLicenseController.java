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

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.ypbin.admin.system.entity.SysLicense;
import cn.ypbin.admin.system.model.query.LicenseQuery;
import cn.ypbin.admin.system.model.req.LicenseApproveReq;
import cn.ypbin.admin.system.model.req.LicenseSaveReq;
import cn.ypbin.admin.system.model.resp.LicenseKeyPairResp;
import cn.ypbin.admin.system.model.resp.LicenseResp;
import cn.ypbin.admin.system.service.SysLicenseService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.security.core.LoginHelper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商业授权签发与管理接口。
 *
 * <p>本应用为授权签发端（供应方）。签发采用双人复核：创建人拟定草稿并提交，审批人（须为他人）
 * 复核后调用国密签名产出授权串。密钥由部署环境托管，不落库。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@RestController
@RequestMapping("/system/license")
@RequiredArgsConstructor
public class SysLicenseController extends BaseController {

    private final SysLicenseService licenseService;

    @GetMapping("/list")
    @SaCheckPermission("system:license:list")
    public R<PageResult<LicenseResp>> list(LicenseQuery query) {
        return ok(licenseService.pageLicense(query));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:license:list")
    public R<LicenseResp> detail(@PathVariable Long id) {
        return ok(licenseService.getLicense(id));
    }

    @Log(value = "新增授权草稿", module = "授权管理")
    @PostMapping
    @SaCheckPermission("system:license:add")
    public R<String> create(@Valid @RequestBody LicenseSaveReq req) {
        return ok(String.valueOf(licenseService.createDraft(req)));
    }

    @Log(value = "修改授权草稿", module = "授权管理")
    @PutMapping("/{id}")
    @SaCheckPermission("system:license:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody LicenseSaveReq req) {
        licenseService.updateDraft(id, req);
        return ok();
    }

    @Log(value = "提交授权审批", module = "授权管理")
    @PutMapping("/{id}/submit")
    @SaCheckPermission("system:license:submit")
    public R<Void> submit(@PathVariable Long id) {
        licenseService.submit(id);
        return ok();
    }

    @Log(value = "审批授权", module = "授权管理")
    @PutMapping("/{id}/approve")
    @SaCheckPermission("system:license:approve")
    public R<Void> approve(@PathVariable Long id, @Valid @RequestBody LicenseApproveReq req) {
        licenseService.approve(id, req, LoginHelper.getUserId());
        return ok();
    }

    @Log(value = "吊销授权", module = "授权管理")
    @PutMapping("/{id}/revoke")
    @SaCheckPermission("system:license:revoke")
    public R<Void> revoke(@PathVariable Long id) {
        licenseService.revoke(id);
        return ok();
    }

    @Log(value = "删除授权", module = "授权管理")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:license:delete")
    public R<Void> delete(@PathVariable Long id) {
        licenseService.deleteLicense(id);
        return ok();
    }

    @Log(value = "生成签发密钥对", module = "授权管理")
    @PostMapping("/generate-key")
    @SaCheckPermission("system:license:genkey")
    public R<LicenseKeyPairResp> generateKey() {
        return ok(licenseService.generateKeyPair());
    }

    /**
     * 下载授权文件（仅 FILE 交付模式的已签发授权）。授权串以 .lic 文件形式输出。
     *
     * @param id       授权主键
     * @param response 响应
     * @throws IOException 写出失败时抛出
     */
    @Log(value = "下载授权文件", module = "授权管理")
    @GetMapping("/{id}/download")
    @SaCheckPermission("system:license:list")
    public void download(@PathVariable Long id, HttpServletResponse response) throws IOException {
        SysLicense entity = licenseService.loadForDownload(id);
        String fileName = URLEncoder.encode(entity.getLicenseId() + ".lic", StandardCharsets.UTF_8);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.getWriter().write(entity.getAuthCode());
        response.getWriter().flush();
    }

    /**
     * 查看内联授权码（仅 CODE 交付模式的已签发授权）。用于复制交付给被授权方。
     *
     * @param id 授权主键
     * @return Base64 授权串
     */
    @GetMapping("/{id}/auth-code")
    @SaCheckPermission("system:license:list")
    public R<String> authCode(@PathVariable Long id) {
        return ok(licenseService.loadAuthCode(id));
    }
}
