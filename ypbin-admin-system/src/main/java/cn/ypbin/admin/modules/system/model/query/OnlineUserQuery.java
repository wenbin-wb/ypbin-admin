/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.query;

import cn.ypbin.starter.crud.model.PageQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 在线用户分页查询条件。
 *
 * <p>在线用户数据来自会话存储而非数据库，分页在服务内完成后切片（内存分页），
 * 故本查询条件不参与任何 SQL 拼接。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class OnlineUserQuery extends PageQuery {

    /** 关键字（按登录账号/昵称模糊匹配） */
    private String keyword;
}
