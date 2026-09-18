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
import cn.ypbin.admin.system.model.query.OnlineUserQuery;
import cn.ypbin.admin.system.model.query.UserQuery;
import cn.ypbin.admin.system.model.req.UserSaveReq;
import cn.ypbin.admin.system.model.resp.OnlineUserResp;
import cn.ypbin.admin.system.model.resp.UserResp;
import cn.ypbin.admin.system.model.vo.UserImportResult;
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
     * 按 ID 取用户，<b>忽略租户过滤</b>，供内部端点跨租户定位用户。
     *
     * <p>刻意与继承自 {@code IService} 的 {@code getById} 区分：后者受租户行拦截器约束，
     * 而本方法服务于登录、第三方回调、令牌校验等<b>尚无租户上下文</b>的路径——匿名链路没有网关
     * 签发的 {@code X-Tenant-Id}，fail-closed（{@code ypbin.tenant.fail-on-missing-tenant=true}）下
     * {@code getById} 会直接抛「缺少租户上下文」。不覆写 {@code getById} 是为了避免所有既有调用者
     * 悄然失去租户隔离（那些调用点在正常请求上下文里依赖隔离）。</p>
     *
     * @param userId 用户 ID
     * @return 用户；不存在返回 {@code null}
     */
    SysUser getByIdGlobal(Long userId);

    boolean verifyPassword(Long userId, String rawPassword);

    long countUsers();

    List<SysUser> searchUsers(String keyword);

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

    /**
     * 分页查询在线用户。
     *
     * <p><strong>内存分页</strong>：在线用户来自会话存储（sa-token 会话），不是数据库，无法下推分页条件，
     * 故先枚举全部在线会话再在服务内切片。分页只减少传输量与渲染量，<strong>不减少</strong>会话枚举与
     * 逐个读取会话的开销（代价为 O(在线会话数) 次会话读取）。关键字过滤语义不变（登录账号/昵称模糊匹配）。</p>
     *
     * @param query 分页与关键字条件
     * @return 分页结果；页码越界时 {@code items} 为空列表，{@code total} 仍为真实总数
     */
    PageResult<OnlineUserResp> pageOnlineUsers(OnlineUserQuery query);
}
