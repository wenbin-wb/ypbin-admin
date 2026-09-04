/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.model.resp;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * 对话角色响应。
 *
 * @author wenbin
 * @since 2026-08-16
 */
@Getter
@Setter
public class AiChatRoleResp {

    private Long id;

    /** 角色名称 */
    private String name;

    /** 角色描述 */
    private String description;

    /** 角色头像 */
    private String avatar;

    /** 分类 */
    private String category;

    /** 推荐模型 */
    private String modelPreference;

    /** 默认温度 */
    private BigDecimal temperature;

    /** 是否内置 */
    private Integer isBuiltin;

    /** 是否已收藏 */
    private Boolean isFavorite;

    /** 排序 */
    private Integer sort;
}
