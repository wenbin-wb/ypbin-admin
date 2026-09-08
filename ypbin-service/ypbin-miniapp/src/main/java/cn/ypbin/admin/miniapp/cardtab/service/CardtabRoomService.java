/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.service;

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoom;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomCreateReq;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomJoinReq;
import cn.ypbin.admin.miniapp.cardtab.model.req.CardtabRoomSettingsReq;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabEventResp;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabHomeSummaryResp;
import cn.ypbin.admin.miniapp.cardtab.model.resp.CardtabRoomDetailResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.Map;

/**
 * 牌账清-房间服务接口。
 *
 * @author wenbin
 * @since 2026-09-08
 */
public interface CardtabRoomService extends BaseService<CardtabRoom> {

    /**
     * 首页概览统计与最近房间。
     */
    CardtabHomeSummaryResp getHomeSummary();

    /**
     * 创建房间。
     */
    CardtabRoomDetailResp createRoom(CardtabRoomCreateReq req);

    /**
     * 获取房间详情。
     */
    CardtabRoomDetailResp getRoomDetail(Long roomId);

    /**
     * 按口令查询房间基础信息。
     */
    CardtabRoomDetailResp getRoomByCode(String roomCode);

    /**
     * 加入房间。
     */
    Map<String, Object> joinRoom(CardtabRoomJoinReq req);

    /**
     * 退出房间。
     */
    void leaveRoom(Long roomId);

    /**
     * 修改房间设置。
     */
    void updateRoomSettings(Long roomId, CardtabRoomSettingsReq req);

    /**
     * 分页查询房间流水。
     */
    PageResult<CardtabEventResp> getRoomEvents(Long roomId, int current, int size);

    /**
     * 分页查询历史房间。
     */
    PageResult<CardtabRoomDetailResp> getHistoryRooms(int current, int size);
}

