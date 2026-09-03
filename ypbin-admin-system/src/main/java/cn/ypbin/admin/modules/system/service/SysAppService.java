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

import cn.ypbin.admin.modules.system.entity.SysApp;
import cn.ypbin.admin.modules.system.model.req.SysAppSaveReq;
import cn.ypbin.admin.modules.system.model.resp.AppCredentialResp;
import cn.ypbin.admin.modules.system.model.resp.AppResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 开放应用服务。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysAppService extends BaseService<SysApp> {

    /**
     * 查询开放应用列表。
     *
     * @return 不含密钥的应用列表
     */
    List<AppResp> listApps();

    /**
     * 新增开放应用（自动生成 accessKey/secretKey）。
     *
     * @param req 请求
     * @return 仅此次返回的应用凭据
     */
    AppCredentialResp createApp(SysAppSaveReq req);

    /**
     * 编辑开放应用（保留 accessKey/secretKey，不重新生成）。
     *
     * @param id  应用 ID
     * @param req 请求
     */
    void updateApp(Long id, SysAppSaveReq req);

    /**
     * 重置 SecretKey（重新生成，accessKey 不变）。
     *
     * @param id 应用 ID
     * @return 新的明文 SecretKey
     */
    AppCredentialResp resetSecret(Long id);

    /**
     * 删除开放应用（校验存在性后逻辑删除）。
     *
     * @param id 应用 ID
     */
    void deleteApp(Long id);
}
