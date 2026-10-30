/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.common.controller;

import cn.ypbin.admin.modules.miniapp.common.model.req.UserProfileUpdateReq;
import cn.ypbin.admin.modules.miniapp.common.model.resp.MiniappUserResp;
import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.service.SysUserService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.storage.core.FileStorageService;
import cn.ypbin.starter.storage.model.FileInfo;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 微信小程序公共基础接口（用户资料、文件上传、小程序码）。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequiredArgsConstructor
public class MiniappCommonController {

    private static final Logger log = LoggerFactory.getLogger(MiniappCommonController.class);

    private final SysUserService userService;
    private final FileStorageService fileStorageService;

    /**
     * 获取当前登录用户资料。
     */
    @GetMapping("/miniapp/user/me")
    public R<MiniappUserResp> getMyProfile() {
        Long userId = currentUserId();
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("获取当前用户信息失败");
        }
        return R.ok(toUserResp(user));
    }

    /**
     * 更新当前用户昵称、头像。
     */
    @PostMapping("/miniapp/user/me")
    public R<MiniappUserResp> updateMyProfile(@RequestBody UserProfileUpdateReq req) {
        Long userId = currentUserId();
        userService.updateMiniappProfile(userId, req.getNickname(), req.getAvatarUrl(), req.getPhone());
        SysUser user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("更新用户信息失败");
        }
        return R.ok(toUserResp(user));
    }

    /**
     * 上传图片/凭证。
     */
    @PostMapping("/miniapp/file/upload")
    public R<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        try {
            FileInfo fileInfo = fileStorageService.upload(file.getInputStream(), file.getOriginalFilename())
                .path("miniapp")
                .execute();
            Map<String, Object> res = new HashMap<>();
            res.put("link", fileInfo.getUrl());
            res.put("url", fileInfo.getUrl());
            res.put("name", fileInfo.getFileName());
            return R.ok(res);
        } catch (IOException e) {
            log.error("小程序上传文件异常", e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取小程序码。
     *
     * <p><b>当前未接入微信 getwxacode 接口</b>，故 {@code qrcodeUrl} 恒为空串。选用「返回空串」
     * 而非抛异常，是因为端侧已按「空即未生成」渲染占位（生成中/生成失败提示），抛异常会让
     * 二维码弹窗直接报错；空串是端侧已处理的既有形态（向后兼容）。</p>
     *
     * <p>TODO 接入微信 getwxacodeUnlimit 后返回真实图片地址；届时本方法需
     * 复用 {@code WechatMiniappProperties} 的 appid/secret 并显式设置超时。</p>
     */
    @GetMapping("/miniapp/wx/qrcode")
    public R<Map<String, String>> getQrcode(
        @RequestParam("scene") String scene,
        @RequestParam(value = "page", required = false) String page,
        @RequestParam(value = "appId", required = false) String appId) {
        // 留痕：调用方无法从空串区分「未实现」与「生成失败」，日志是唯一线索
        log.warn("小程序码生成尚未接入微信接口，返回空地址；scene={}，page={}，appId={}",
            scene, page, appId);
        Map<String, String> res = new HashMap<>();
        res.put("scene", scene);
        res.put("page", page != null ? page : "");
        res.put("qrcodeUrl", "");
        return R.ok(res);
    }

    private Long currentUserId() {
        return UserContext.getUserIdSafely()
            .orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    private MiniappUserResp toUserResp(SysUser user) {
        MiniappUserResp resp = new MiniappUserResp();
        resp.setId(user.getId());
        String username = user.getUsername() != null ? user.getUsername() : "";
        resp.setOpenid(username.startsWith("wx_") ? username.substring(3) : username);
        resp.setNickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getRealName());
        resp.setAvatarUrl(user.getAvatar());
        String name = resp.getNickname();
        resp.setAvatarText(StringUtils.hasText(name) ? name.substring(name.length() - 1) : "我");
        resp.setPhone(user.getPhone());
        return resp;
    }
}
