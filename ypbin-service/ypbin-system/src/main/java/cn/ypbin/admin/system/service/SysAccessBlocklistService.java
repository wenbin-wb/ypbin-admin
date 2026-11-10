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

import cn.ypbin.admin.system.entity.SysAccessBlocklist;
import cn.ypbin.admin.system.model.query.AccessBlocklistQuery;
import cn.ypbin.admin.system.model.req.AccessBlocklistBanReq;
import cn.ypbin.admin.system.model.req.AccessBlocklistSaveReq;
import cn.ypbin.admin.system.model.resp.AccessBlocklistResp;
import cn.ypbin.admin.system.model.resp.AutoBanResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 访问黑名单服务。
 *
 * @author wenbin
 * @since 2026-11-05
 */
public interface SysAccessBlocklistService extends BaseService<SysAccessBlocklist> {

    /**
     * 分页查询规则。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AccessBlocklistResp> pageBlocklists(AccessBlocklistQuery query);

    /**
     * 新增规则并全量下发。
     *
     * @param req 请求
     */
    void createBlocklist(AccessBlocklistSaveReq req);

    /**
     * 修改规则并全量下发。
     *
     * @param id  规则 ID
     * @param req 请求
     */
    void updateBlocklist(Long id, AccessBlocklistSaveReq req);

    /**
     * 删除规则并全量下发。
     *
     * @param id 规则 ID
     */
    void deleteBlocklist(Long id);

    /**
     * 当前自动/临时封禁列表。
     *
     * @return 封禁记录
     */
    List<AutoBanResp> listAutoBans();

    /**
     * 解除某个 IP 的封禁。
     *
     * @param clientIp 客户端 IP
     */
    void unban(String clientIp);

    /**
     * 临时封禁一个 IP（带 TTL，与长期规则区分）。
     *
     * @param req 请求
     */
    void ban(AccessBlocklistBanReq req);

    /**
     * 把数据库启用规则全量重新下发到存储。
     */
    void refreshRules();
}
