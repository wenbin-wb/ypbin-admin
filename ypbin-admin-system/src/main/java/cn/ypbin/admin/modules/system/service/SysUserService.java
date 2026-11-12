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
import cn.ypbin.admin.modules.system.model.query.OnlineUserQuery;
import cn.ypbin.admin.modules.system.model.query.UserQuery;
import cn.ypbin.admin.modules.system.model.req.UserRegisterReq;
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

    /**
     * 用户自助注册：按系统参数确定归属租户与默认角色后落库。
     *
     * <p><b>与 {@link #createUser(UserSaveReq)} 的区别</b>：后者面向管理端，租户来自当前登录身份、
     * 角色由管理员显式分配；本方法面向匿名入口，没有登录身份，归属租户与默认角色只能来自系统参数
     * （{@code REGISTER_DEFAULT_TENANT_ID} / {@code REGISTER_DEFAULT_ROLE_CODE}），且开关
     * {@code REGISTER_ENABLED} 未开启时一律拒绝。</p>
     *
     * <p>失败语义：开关未开启、参数缺失/非法、租户或角色不可用、用户名/手机号重复、密码不符合策略
     * 均抛业务异常，不做任何静默降级。</p>
     *
     * @param req 注册请求（不含租户字段）
     * @return 新建用户
     */
    SysUser registerUser(UserRegisterReq req);

    void updateUser(Long id, UserSaveReq req);

    void updateStatus(Long id, Integer status);

    void deleteUser(Long id);

    void resetPassword(Long id, String password);

    void assignRoles(Long id, List<Long> roleIds);

    void exportUsers(UserQuery query, HttpServletResponse response);

    void downloadImportTemplate(HttpServletResponse response);

    UserImportResult importUsers(MultipartFile file);

    PageResult<OnlineUserResp> pageOnlineUsers(OnlineUserQuery query);

    /**
     * 按会话令牌指纹强制下线（列表不再回传 token 原文，踢人只能凭指纹反查）。
     *
     * @param tokenHash 会话令牌指纹
     */
    void kickoutByTokenHash(String tokenHash);
}
