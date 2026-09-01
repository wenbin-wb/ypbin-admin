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

import cn.ypbin.admin.system.entity.SysClient;
import cn.ypbin.admin.system.model.req.SysClientSaveReq;
import cn.ypbin.admin.system.model.resp.ClientCredentialResp;
import cn.ypbin.admin.system.model.resp.ClientResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 登录客户端服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysClientService extends BaseService<SysClient> {

    /**
     * 查询登录客户端列表。
     *
     * @return 不含密钥的客户端列表
     */
    List<ClientResp> listClients();

    /**
     * 新增客户端（自动生成 clientSecret）。
     *
     * @param req 请求
     * @return 仅此次返回的客户端凭据
     */
    ClientCredentialResp createClient(SysClientSaveReq req);

    /**
     * 编辑客户端（保留 clientSecret，不重新生成）。
     *
     * @param id  客户端 ID
     * @param req 请求
     */
    void updateClient(Long id, SysClientSaveReq req);

    /**
     * 重置客户端密钥（重新生成 clientSecret）。
     *
     * @param id 客户端 ID
     * @return 新的明文密钥
     */
    ClientCredentialResp resetSecret(Long id);

    /**
     * 删除登录客户端（校验存在性后逻辑删除）。
     *
     * @param id 客户端 ID
     */
    void deleteClient(Long id);
}
