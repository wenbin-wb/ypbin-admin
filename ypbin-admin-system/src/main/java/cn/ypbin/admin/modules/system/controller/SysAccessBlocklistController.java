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
import cn.ypbin.starter.security.platform.PlatformAccess;
import cn.ypbin.admin.modules.system.model.query.AccessBlocklistQuery;
import cn.ypbin.admin.modules.system.model.req.AccessBlocklistBanReq;
import cn.ypbin.admin.modules.system.model.req.AccessBlocklistSaveReq;
import cn.ypbin.admin.modules.system.model.resp.AccessBlocklistResp;
import cn.ypbin.admin.modules.system.model.resp.AutoBanResp;
import cn.ypbin.admin.modules.system.service.SysAccessBlocklistService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.tools.idempotent.Idempotent;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问黑名单管理接口。
 *
 * @author wenbin
 * @since 2026-11-04
 */
@RestController
@RequestMapping("/system/security/blocklist")
@RequiredArgsConstructor
@PlatformAccess
public class SysAccessBlocklistController {

    private final SysAccessBlocklistService blocklistService;

    @GetMapping("/list")
    @SaCheckPermission("system:blocklist:list")
    public R<PageResult<AccessBlocklistResp>> list(@Valid AccessBlocklistQuery query) {
        return R.ok(blocklistService.pageBlocklists(query));
    }

    @Idempotent
    @Log(value = "新增访问黑名单", module = "安全防护")
    @PostMapping
    @SaCheckPermission("system:blocklist:add")
    public R<Void> create(@Valid @RequestBody AccessBlocklistSaveReq req) {
        blocklistService.createBlocklist(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "修改访问黑名单", module = "安全防护")
    @PutMapping("/{id}")
    @SaCheckPermission("system:blocklist:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AccessBlocklistSaveReq req) {
        blocklistService.updateBlocklist(id, req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "删除访问黑名单", module = "安全防护")
    @DeleteMapping("/{id}")
    @SaCheckPermission("system:blocklist:delete")
    public R<Void> delete(@PathVariable Long id) {
        blocklistService.deleteBlocklist(id);
        return R.ok();
    }

    @GetMapping("/auto-ban")
    @SaCheckPermission("system:blocklist:list")
    public R<List<AutoBanResp>> autoBans() {
        return R.ok(blocklistService.listAutoBans());
    }

    @Idempotent
    @Log(value = "临时封禁 IP", module = "安全防护")
    @PostMapping("/ban")
    @SaCheckPermission("system:blocklist:ban")
    public R<Void> ban(@Valid @RequestBody AccessBlocklistBanReq req) {
        blocklistService.ban(req);
        return R.ok();
    }

    @Idempotent
    @Log(value = "解除自动封禁", module = "安全防护")
    @DeleteMapping("/auto-ban/{clientIp}")
    @SaCheckPermission("system:blocklist:unban")
    public R<Void> unban(@PathVariable String clientIp) {
        blocklistService.unban(clientIp);
        return R.ok();
    }

    @Idempotent
    @Log(value = "刷新访问黑名单", module = "安全防护")
    @PostMapping("/refresh")
    @SaCheckPermission("system:blocklist:edit")
    public R<Void> refresh() {
        blocklistService.refreshRules();
        return R.ok();
    }
}
