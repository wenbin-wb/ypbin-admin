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

import cn.ypbin.admin.modules.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户名查询用户，<b>包含逻辑删除行</b>（自定义 SQL 不受 {@code @TableLogic} 过滤）。
     *
     * <p>小程序用户由 openid 派生固定用户名；逻辑删除后再次登录必须能发现旧行，
     * 否则会走 insert 并撞上 {@code uk_username}。</p>
     *
     * @param username 用户名
     * @return 用户实体（可能是已删除行），不存在时为 null
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUser selectByUsernameIncludeDeleted(@Param("username") String username);

    /**
     * 恢复逻辑删除的小程序用户（仅清理删除标记，其余字段由调用方按正常流程更新）。
     *
     * @param username 用户名
     * @return 受影响行数
     */
    @Update("UPDATE sys_user SET is_deleted = 0, update_time = NOW() WHERE username = #{username} AND is_deleted = 1")
    int restoreDeletedByUsername(@Param("username") String username);
}
