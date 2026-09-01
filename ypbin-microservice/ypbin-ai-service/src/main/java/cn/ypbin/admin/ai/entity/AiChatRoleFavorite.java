/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.entity;

import cn.ypbin.starter.tenant.core.TenantBaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户角色收藏实体。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
@TableName("ai_chat_role_favorite")
public class AiChatRoleFavorite extends TenantBaseEntity {

    /** 用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
