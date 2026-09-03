/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.mapper;

import cn.ypbin.admin.modules.system.entity.SysMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 菜单 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询用户可访问的全部菜单（经 用户-角色-菜单 三级关联，去重）。
     *
     * @param userId 用户 ID
     * @return 菜单列表
     */
    @Select("""
        SELECT DISTINCT m.* FROM sys_menu m
        INNER JOIN sys_role_menu rm ON rm.menu_id = m.id
        INNER JOIN sys_role r ON r.id = rm.role_id
        INNER JOIN sys_user_role ur ON ur.role_id = r.id
        INNER JOIN sys_user u ON u.id = ur.user_id
        WHERE ur.user_id = #{userId}
          AND u.tenant_id = r.tenant_id
          AND u.status = 1 AND u.is_deleted = 0
          AND r.status = 1 AND r.is_deleted = 0
          AND m.status = 1 AND m.is_deleted = 0
        ORDER BY m.sort ASC, m.id ASC
        """)
    List<SysMenu> selectByUserId(@Param("userId") Long userId);
}
