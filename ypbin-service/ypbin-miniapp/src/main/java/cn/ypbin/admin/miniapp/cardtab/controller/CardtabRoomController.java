/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.controller;

import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomCreateReq;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomJoinReq;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomSettingsReq;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabEventResp;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabRoomDetailResp;
import cn.ypbin.admin.miniapp.cardtab.service.CardtabRoomService;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.crud.model.PageResult;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 牌账清-房间控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/cardtab/rooms")
@RequiredArgsConstructor
public class CardtabRoomController {

    private final CardtabRoomService roomService;

    /**
     * 创建房间。
     */
    @PostMapping
    public R<CardtabRoomDetailResp> createRoom(@Valid @RequestBody CardtabRoomCreateReq req) {
        return R.ok(roomService.createRoom(req));
    }

    /**
     * 房间详情。
     */
    @GetMapping("/{roomId}")
    public R<CardtabRoomDetailResp> getRoomDetail(@PathVariable Long roomId) {
        return R.ok(roomService.getRoomDetail(roomId));
    }

    /**
     * 按口令查询房间。
     */
    @GetMapping("/code/{roomCode}")
    public R<CardtabRoomDetailResp> getRoomByCode(@PathVariable String roomCode) {
        return R.ok(roomService.getRoomByCode(roomCode));
    }

    /**
     * 加入房间。
     */
    @PostMapping("/join")
    public R<Map<String, Object>> joinRoom(@RequestBody CardtabRoomJoinReq req) {
        return R.ok(roomService.joinRoom(req));
    }

    /**
     * 退出房间。
     */
    @PostMapping("/{roomId}/leave")
    public R<Void> leaveRoom(@PathVariable Long roomId) {
        roomService.leaveRoom(roomId);
        return R.ok();
    }

    /**
     * 修改房间设置（仅房主）。
     */
    @PutMapping("/{roomId}/settings")
    public R<Void> updateRoomSettings(@PathVariable Long roomId, @RequestBody CardtabRoomSettingsReq req) {
        roomService.updateRoomSettings(roomId, req);
        return R.ok();
    }

    /**
     * 分页查询房间流水。
     */
    @GetMapping("/{roomId}/events")
    public R<PageResult<CardtabEventResp>> getRoomEvents(
        @PathVariable Long roomId,
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "20") int size) {
        return R.ok(roomService.getRoomEvents(roomId, current, size));
    }

    /**
     * 分页查询历史房间。
     */
    @GetMapping("/history")
    public R<PageResult<CardtabRoomDetailResp>> getHistoryRooms(
        @RequestParam(defaultValue = "1") int current,
        @RequestParam(defaultValue = "10") int size) {
        return R.ok(roomService.getHistoryRooms(current, size));
    }
}
