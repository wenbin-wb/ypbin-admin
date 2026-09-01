/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysDictItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典项 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    /**
     * 按字典编码查询字典项（经字典类型表关联），按 sort 升序。
     *
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    @Select("""
        SELECT i.* FROM sys_dict_item i
        INNER JOIN sys_dict d ON d.id = i.dict_id
        WHERE d.code = #{dictCode} AND i.is_deleted = 0 AND d.is_deleted = 0
        ORDER BY i.sort ASC
        """)
    List<SysDictItem> selectByDictCode(@Param("dictCode") String dictCode);
}
