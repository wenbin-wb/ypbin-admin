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

import cn.ypbin.admin.modules.system.model.query.PlatformUserQuery;
import cn.ypbin.admin.modules.system.model.resp.PlatformUserResp;
import cn.ypbin.starter.crud.model.PageResult;

/**
 * 平台账号服务。
 *
 * <p>平台账号由启动引导按配置创建，这里只提供查看与两个安全操作（改状态、重置密码），
 * 不提供新增、删除与角色分配，避免在运行期制造或摧毁平台级身份。</p>
 *
 * @author wenbin
 * @since 2026-11-17
 */
public interface PlatformUserService {

    /**
     * 分页查询平台账号。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<PlatformUserResp> pagePlatformUsers(PlatformUserQuery query);

    /**
     * 修改平台账号状态。
     *
     * @param id     平台账号 ID
     * @param status 目标状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 重置平台账号密码。
     *
     * @param id       平台账号 ID
     * @param password 明文新密码
     */
    void resetPassword(Long id, String password);
}
