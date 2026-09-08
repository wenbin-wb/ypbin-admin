/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.service;

import cn.ypbin.admin.miniapp.familymenu.entity.FamilymenuRoom;
import cn.ypbin.admin.miniapp.familymenu.model.req.FamilymenuRoomCreateReq;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuMemberResp;
import cn.ypbin.admin.miniapp.familymenu.model.resp.FamilymenuRoomDetailResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 家庭菜单-家庭餐厅服务接口。
 *
 * @author wenbin
 * @since 2026-09-08
 */
public interface FamilymenuRoomService extends BaseService<FamilymenuRoom> {

    /**
     * 创建家庭餐厅。
     */
    FamilymenuRoomDetailResp createRoom(FamilymenuRoomCreateReq req);

    /**
     * 餐厅详情。
     */
    FamilymenuRoomDetailResp getRoomDetail(Long roomId);

    /**
     * 根据邀请码加入餐厅。
     */
    FamilymenuRoomDetailResp joinRoom(String inviteCode);

    /**
     * 退出餐厅。
     */
    void leaveRoom(Long roomId);

    /**
     * 解散餐厅（仅房主）。
     */
    void disbandRoom(Long roomId);

    /**
     * 餐厅成员列表。
     */
    List<FamilymenuMemberResp> getMemberList(Long roomId);

    /**
     * 切换成员角色（CHEF/DINER）。
     */
    void updateMemberRole(Long memberId, String role);

    /**
     * 获取我加入的所有餐厅列表。
     */
    List<FamilymenuRoomDetailResp> getMyRooms();

    /**
     * 获取指定或当前默认餐厅信息。
     */
    FamilymenuRoomDetailResp getMyRoom(Long roomId);
}

