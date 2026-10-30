/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.service;

import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.model.query.UserQuery;
import cn.ypbin.admin.modules.system.model.req.UserSaveReq;
import cn.ypbin.admin.modules.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.modules.system.model.resp.UserResp;
import cn.ypbin.admin.modules.system.model.vo.UserImportResult;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysUserService extends BaseService<SysUser> {

    SysUser getByUsername(String username);

    SysUser getByPhone(String phone);

    /**
     * 按用户名获取小程序用户，不存在时创建（供小程序匿名登录入口使用）。
     *
     * @param username 用户名（由小程序端身份派生）
     * @param nickname 昵称，可空
     * @param avatar   头像地址，可空
     * @return 用户实体
     */
    SysUser getOrCreateMiniappUser(String username, String nickname, String avatar);

    /**
     * 更新小程序用户资料：仅更新显式提供的字段，未提供（空白）的字段保持不变。
     *
     * @param userId   用户 ID
     * @param nickname 昵称，可为空
     * @param avatar   头像地址，可为空
     * @param phone    手机号，可为空
     */
    void updateMiniappProfile(Long userId, String nickname, String avatar, String phone);

    void updateLastLoginTime(Long userId);

    PageResult<UserResp> pageUsers(UserQuery query);

    UserResp getUserDetail(Long id);

    void createUser(UserSaveReq req);

    void updateUser(Long id, UserSaveReq req);

    void updateStatus(Long id, Integer status);

    void deleteUser(Long id);

    void resetPassword(Long id, String password);

    void assignRoles(Long id, List<Long> roleIds);

    void exportUsers(UserQuery query, HttpServletResponse response);

    void downloadImportTemplate(HttpServletResponse response);

    UserImportResult importUsers(MultipartFile file);

    List<OnlineUserResp> listOnlineUsers(String keyword);
}
