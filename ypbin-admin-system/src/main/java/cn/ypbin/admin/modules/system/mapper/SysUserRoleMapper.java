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

import cn.ypbin.admin.modules.system.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户-角色关联 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 查询用户已分配的角色 ID 集合。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}
