/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.controller;

import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuRoomCreateReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuMemberResp;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuRoomDetailResp;
import cn.ypbin.admin.miniapp.familymenu.service.FamilymenuRoomService;
import cn.ypbin.starter.core.model.R;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家庭菜单-家庭餐厅与成员控制器。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/familymenu")
@RequiredArgsConstructor
public class FamilymenuRoomController {

    private final FamilymenuRoomService roomService;

    /**
     * 创建餐厅。
     */
    @PostMapping("/room/submit")
    public R<FamilymenuRoomDetailResp> createRoom(@Valid @RequestBody FamilymenuRoomCreateReq req) {
        return R.ok(roomService.createRoom(req));
    }

    /**
     * 餐厅详情。
     */
    @GetMapping("/room/detail")
    public R<FamilymenuRoomDetailResp> getRoomDetail(@RequestParam("id") Long id) {
        return R.ok(roomService.getRoomDetail(id));
    }

    /**
     * 根据邀请码加入餐厅。
     */
    @PostMapping("/room/join")
    public R<FamilymenuRoomDetailResp> joinRoom(@RequestBody Map<String, String> body) {
        String inviteCode = body.get("inviteCode");
        return R.ok(roomService.joinRoom(inviteCode));
    }

    /**
     * 退出餐厅。
     */
    @PostMapping("/room/leave")
    public R<Void> leaveRoom(@RequestParam("roomId") Long roomId) {
        roomService.leaveRoom(roomId);
        return R.ok();
    }

    /**
     * 解散餐厅。
     */
    @PostMapping("/room/disband")
    public R<Void> disbandRoom(@RequestParam("roomId") Long roomId) {
        roomService.disbandRoom(roomId);
        return R.ok();
    }

    /**
     * 餐厅成员列表。
     */
    @GetMapping("/member/list")
    public R<List<FamilymenuMemberResp>> getMemberList(@RequestParam("roomId") Long roomId) {
        return R.ok(roomService.getMemberList(roomId));
    }

    /**
     * 修改成员角色。
     */
    @PostMapping("/member/role")
    public R<Void> updateMemberRole(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String role = body.get("role").toString();
        roomService.updateMemberRole(id, role);
        return R.ok();
    }

    /**
     * 获取我加入的所有餐厅。
     */
    @GetMapping("/member/my-rooms")
    public R<List<FamilymenuRoomDetailResp>> getMyRooms() {
        return R.ok(roomService.getMyRooms());
    }

    /**
     * 获取当前选中餐厅。
     */
    @GetMapping("/member/my-room")
    public R<FamilymenuRoomDetailResp> getMyRoom(@RequestParam(value = "roomId", required = false) Long roomId) {
        return R.ok(roomService.getMyRoom(roomId));
    }
}
