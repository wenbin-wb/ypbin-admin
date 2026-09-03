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

import cn.ypbin.admin.modules.system.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 角色 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询用户拥有的角色（经用户-角色关联表）。
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    @Select("""
        SELECT DISTINCT r.* FROM sys_role r
        INNER JOIN sys_user_role ur ON ur.role_id = r.id
        INNER JOIN sys_user u ON u.id = ur.user_id
        WHERE ur.user_id = #{userId}
          AND u.tenant_id = r.tenant_id
          AND u.status = 1 AND u.is_deleted = 0
          AND r.status = 1 AND r.is_deleted = 0
        """)
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    /**
     * 判断用户是否绑定有效的平台超级管理员角色。
     *
     * @param userId 用户 ID
     * @return 匹配数量
     */
    @Select("""
        SELECT COUNT(1) FROM sys_user u
        INNER JOIN sys_user_role ur ON ur.user_id = u.id
        INNER JOIN sys_role r ON r.id = ur.role_id
        WHERE u.id = #{userId}
          AND u.user_type = 'PLATFORM'
          AND r.role_type = 'PLATFORM_SUPER'
          AND u.tenant_id = r.tenant_id
          AND u.status = 1 AND u.is_deleted = 0
          AND r.status = 1 AND r.is_deleted = 0
        """)
    long countPlatformSuperByUserId(@Param("userId") Long userId);
}
