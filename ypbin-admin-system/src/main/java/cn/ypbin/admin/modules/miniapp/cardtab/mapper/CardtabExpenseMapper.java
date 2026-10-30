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

import cn.ypbin.admin.modules.miniapp.cardtab.entity.CardtabExpense;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 牌账清-支出记录 Mapper。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Mapper
public interface CardtabExpenseMapper extends BaseMapper<CardtabExpense> {

    /**
     * 按房间统计未撤销的支出笔数（单次 IN 查询，避免逐房间计数形成 N+1）。
     *
     * <p>逻辑删除由 {@code BaseEntity.is_deleted} 的 {@code @TableLogic} 负责，但本方法是自定义 SQL，
     * 拦截器不会改写它，故<b>必须手动带上 {@code is_deleted = 0}</b>；撤销的支出不计入笔数。</p>
     *
     * @param roomIds 房间 ID 集合（调用方须保证非空，空集合会生成非法 SQL）
     * @return 每行 {@code room_id} 与 {@code expenseCount}
     */
    @Select("""
        <script>
        SELECT room_id AS roomId, COUNT(1) AS expenseCount
        FROM cardtab_expense
        WHERE is_deleted = 0
          AND expense_status = 'NORMAL'
          AND room_id IN
          <foreach collection="roomIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY room_id
        </script>
        """)
    List<Map<String, Object>> countByRoomIds(@Param("roomIds") List<Long> roomIds);
}
