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
import cn.ypbin.admin.system.annotation.PlatformAccess;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.system.service.SysUserService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.controller.BaseController;
import cn.ypbin.starter.security.online.OnlineUser;
import cn.ypbin.starter.security.online.OnlineUserService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线用户管理接口。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestController
@RequestMapping("/system/online-user")
@RequiredArgsConstructor
@PlatformAccess
public class OnlineUserController extends BaseController {

    private final OnlineUserService onlineUserService;
    private final SysUserService userService;

    @GetMapping("/list")
    @SaCheckPermission("system:online-user:list")
    public R<List<OnlineUserResp>> list(@RequestParam(required = false) String keyword) {
        List<OnlineUser> users = keyword != null && !keyword.isBlank()
            ? onlineUserService.list(keyword)
            : onlineUserService.list();
        // 按 userId 批量查真实姓名补充展示
        Map<Long, String> realNameById = resolveRealNames(users);
        List<OnlineUserResp> resp = users.stream().map(u -> {
            OnlineUserResp r = new OnlineUserResp();
            BeanUtils.copyProperties(u, r);
            r.setRealName(realNameById.get(u.getUserId()));
            return r;
        }).toList();
        return ok(resp);
    }

    private Map<Long, String> resolveRealNames(List<OnlineUser> users) {
        List<Long> ids = users.stream().map(OnlineUser::getUserId).filter(Objects::nonNull)
            .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(ids).stream()
            .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
    }

    @DeleteMapping("/{token}")
    @SaCheckPermission("system:online-user:kickout")
    public R<Void> kickout(@PathVariable String token) {
        onlineUserService.kickoutByToken(token);
        return ok();
    }
}
