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

import cn.ypbin.admin.modules.auth.config.WechatMiniappProperties;
import cn.ypbin.admin.modules.system.model.resp.NoticeResp;
import cn.ypbin.admin.modules.system.service.SysNoticeService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序公告接口。
 *
 * <p>公告数据在 system 域（{@code sys_notice}）；「哪些公告对外可见」的口径收口在
 * {@link SysNoticeService}，本层只做转发与错误暴露。</p>
 *
 * <p><b>客户端归属</b>：公告按投放客户端筛选，本入口固定按配置的小程序客户端
 * （{@code ypbin.wechat.miniapp.client-id}）取公告，端侧不再自报客户端。
 * {@code targetType}/{@code targetCode} 为历史兼容参数，不参与筛选。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@RestController
@RequestMapping("/miniapp/notice")
@RequiredArgsConstructor
public class MiniappNoticeController {

    private final SysNoticeService noticeService;
    private final WechatMiniappProperties wechatMiniappProperties;

    /**
     * 最新一条公告（无公告时 data 为 null）。
     */
    @GetMapping("/latest")
    public R<NoticeResp> latest(
        @RequestParam(value = "targetType", required = false) String targetType,
        @RequestParam(value = "targetCode", required = false) String targetCode) {
        List<NoticeResp> list = noticeService.listPublishedNotices(wechatMiniappProperties.getClientId());
        if (list.isEmpty()) {
            return R.ok(null);
        }
        return R.ok(list.getFirst());
    }

    /**
     * 公告详情。
     */
    @GetMapping("/detail")
    public R<NoticeResp> detail(@RequestParam("id") Long id) {
        NoticeResp notice = noticeService.getPublishedNotice(id, wechatMiniappProperties.getClientId());
        if (notice == null) {
            throw new BusinessException("公告不存在或已下架");
        }
        return R.ok(notice);
    }
}
