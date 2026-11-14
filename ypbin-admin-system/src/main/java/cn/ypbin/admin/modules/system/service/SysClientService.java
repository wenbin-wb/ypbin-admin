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

import cn.ypbin.admin.modules.system.entity.SysClient;
import cn.ypbin.admin.modules.system.model.req.SysClientSaveReq;
import cn.ypbin.admin.modules.system.model.resp.ClientCredentialResp;
import cn.ypbin.admin.modules.system.model.resp.ClientResp;
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
     * 新增客户端。
     *
     * <p>默认不生成密钥：浏览器、小程序等端无法保管密钥，无密钥即可正常登录；
     * 需要密钥的端由管理员通过「重置密钥」显式开启。</p>
     *
     * @param req 请求
     * @return 新建的客户端（不含密钥）
     */
    ClientResp createClient(SysClientSaveReq req);

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
     * 清除客户端密钥（恢复为免密钥登录）。
     *
     * @param id 客户端 ID
     */
    void clearSecret(Long id);

    /**
     * 修改客户端启停状态。
     *
     * @param id     客户端 ID
     * @param status 1 启用、0 禁用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除登录客户端（校验存在性后逻辑删除）。
     *
     * @param id 客户端 ID
     */
    void deleteClient(Long id);
}
