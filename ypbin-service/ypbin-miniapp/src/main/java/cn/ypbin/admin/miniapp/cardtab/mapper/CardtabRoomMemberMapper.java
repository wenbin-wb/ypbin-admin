/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.mapper;

import cn.ypbin.admin.miniapp.cardtab.entity.CardtabRoomMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 牌账清-房间成员 Mapper。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Mapper
public interface CardtabRoomMemberMapper extends BaseMapper<CardtabRoomMember> {
}
