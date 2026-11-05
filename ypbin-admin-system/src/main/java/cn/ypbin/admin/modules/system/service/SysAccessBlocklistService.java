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

import cn.ypbin.admin.modules.system.entity.SysAccessBlocklist;
import cn.ypbin.admin.modules.system.model.query.AccessBlocklistQuery;
import cn.ypbin.admin.modules.system.model.req.AccessBlocklistBanReq;
import cn.ypbin.admin.modules.system.model.req.AccessBlocklistSaveReq;
import cn.ypbin.admin.modules.system.model.resp.AccessBlocklistResp;
import cn.ypbin.admin.modules.system.model.resp.AutoBanResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 访问黑名单服务。
 *
 * @author wenbin
 * @since 2026-11-04
 */
public interface SysAccessBlocklistService extends BaseService<SysAccessBlocklist> {

    /**
     * 分页查询黑名单规则。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AccessBlocklistResp> pageBlocklists(AccessBlocklistQuery query);

    /**
     * 新增规则并下发。
     *
     * @param req 请求
     */
    void createBlocklist(AccessBlocklistSaveReq req);

    /**
     * 修改规则并下发。
     *
     * @param id  规则 ID
     * @param req 请求
     */
    void updateBlocklist(Long id, AccessBlocklistSaveReq req);

    /**
     * 删除规则并下发。
     *
     * @param id 规则 ID
     */
    void deleteBlocklist(Long id);

    /**
     * 当前自动封禁记录。
     *
     * @return 自动封禁列表
     */
    List<AutoBanResp> listAutoBans();

    /**
     * 解除指定 IP 的自动封禁。
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
     * 重新下发数据库中的全部规则。
     */
    void refreshRules();
}
