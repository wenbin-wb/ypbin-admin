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
import cn.ypbin.admin.system.model.req.UserRegisterReq;
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

    long countUsers(Long tenantId);

    List<SysUser> searchUsers(String keyword, Long tenantId);

    void updateLastLoginTime(Long userId);

    PageResult<UserResp> pageUsers(UserQuery query);

    UserResp getUserDetail(Long id);

    void createUser(UserSaveReq req);

    /**
     * 用户自助注册：按系统参数确定归属租户与默认角色后落库。
     *
     * <p><b>与 {@link #createUser(UserSaveReq)} 的区别</b>：后者面向管理端，租户来自当前登录身份头、
     * 角色由管理员显式分配；本方法面向匿名入口，没有身份头，归属租户与默认角色只能来自系统参数
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

    /**
     * 按会话令牌指纹强制下线（列表不再回传 token 原文，踢人只能凭指纹反查）。
     *
     * @param tokenHash 会话令牌指纹
     */
    void kickoutByTokenHash(String tokenHash);
}
