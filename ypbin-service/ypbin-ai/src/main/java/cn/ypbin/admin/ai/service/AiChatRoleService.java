/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.service;

import cn.ypbin.admin.ai.model.req.AiChatRoleSaveReq;
import cn.ypbin.admin.ai.model.resp.AiChatRoleResp;
import java.util.List;

/**
 * AI 对话角色服务接口。
 *
 * @author wenbin
 * @since 2026-08-16
 */
public interface AiChatRoleService {

    /** 角色列表（内置 + 当前租户自定义，含收藏状态）；不带参数仅查启用角色 */
    List<AiChatRoleResp> listRoles();

    /**
     * 角色列表并支持按状态过滤。
     *
     * @param status 状态过滤（null=仅启用，与默认行为一致；0/1 精确过滤，供管理端找回已停用角色）
     * @return 角色列表
     */
    List<AiChatRoleResp> listRoles(Integer status);

    /** 创建自定义角色 */
    Long createRole(AiChatRoleSaveReq req);

    /** 修改角色（仅限自定义角色） */
    void updateRole(Long id, AiChatRoleSaveReq req);

    /** 删除角色（仅限自定义角色） */
    void deleteRole(Long id);

    /** 收藏/取消收藏角色 */
    void toggleFavorite(Long roleId);
}
