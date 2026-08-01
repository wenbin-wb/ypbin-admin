/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 用户服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.req.UserSaveReq;
import cn.ypbin.admin.system.model.resp.UserResp;
import cn.ypbin.starter.crud.model.PageResult;

public interface SysUserService extends BaseService<SysUser> {

    /**
     * 按用户名查询用户（全局唯一，忽略租户上下文）。
     *
     * @param username 登录账号
     * @return 用户，不存在返回 null
     */
    SysUser getByUsername(String username);

    /**
     * 按手机号查询用户（全局唯一，忽略租户上下文）。
     *
     * @param phone 手机号
     * @return 用户，不存在返回 null
     */
    SysUser getByPhone(String phone);

    /**
     * 更新用户最后登录时间。
     *
     * @param userId 用户 ID
     */
    void updateLastLoginTime(Long userId);

    /**
     * 分页查询用户列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<UserResp> pageUsers(UserQuery query);

    /**
     * 查询用户详情（含已分配角色 ID）。
     *
     * @param id 用户 ID
     * @return 用户详情
     */
    UserResp getUserDetail(Long id);

    /**
     * 新增用户（密码加密 + 用户名查重 + 事务内分配角色）。
     *
     * @param req 请求
     */
    void createUser(UserSaveReq req);

    /**
     * 编辑用户（用户名查重排除自身 + 可选改密 + 事务内重分配角色）。
     *
     * @param id  用户 ID
     * @param req 请求
     */
    void updateUser(Long id, UserSaveReq req);

    /**
     * 删除用户（同时清理用户-角色关联）。
     *
     * @param id 用户 ID
     */
    void deleteUser(Long id);

    /**
     * 查询当前登录用户的个人信息。
     *
     * @return 用户信息
     */
    UserResp getProfile();

    /**
     * 更新当前登录用户的个人信息（仅展示类字段）。
     *
     * @param req 请求
     */
    void updateProfile(cn.ypbin.admin.system.model.req.ProfileUpdateReq req);

    /**
     * 修改当前登录用户密码（校验原密码 + 密码策略 + 历史密码不重复）。
     *
     * @param req 请求
     */
    void changePassword(cn.ypbin.admin.system.model.req.ChangePasswordReq req);
}
