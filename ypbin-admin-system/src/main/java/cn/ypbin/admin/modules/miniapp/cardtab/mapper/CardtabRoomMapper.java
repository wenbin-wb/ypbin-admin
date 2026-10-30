/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.miniapp.cardtab.mapper;

import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabRoom;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 牌账清-房间 Mapper。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Mapper
public interface CardtabRoomMapper extends BaseMapper<CardtabRoom> {

    /**
     * 按主键读取房间并加行锁（调用方须处于事务中）。
     *
     * <p>记账、撤销、结算都会改动房间账目；统一先锁房间行，使这些写操作串行，
     * 避免「两人同时记账覆盖积分」「并发结算写出两份快照」「结算与记账交错」。</p>
     *
     * @param id 房间 ID
     * @return 房间实体，不存在时为 null
     */
    @Select("SELECT * FROM cardtab_room WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    CardtabRoom selectByIdForUpdate(@Param("id") Long id);
}
