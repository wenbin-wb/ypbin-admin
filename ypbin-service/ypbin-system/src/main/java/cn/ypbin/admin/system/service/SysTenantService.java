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

import cn.ypbin.admin.system.entity.SysTenant;
import cn.ypbin.admin.system.model.req.TenantSaveReq;
import cn.ypbin.admin.system.model.resp.TenantResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;

/**
 * 租户服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysTenantService extends BaseService<SysTenant> {

    List<TenantResp> listTenants();

    void createTenant(TenantSaveReq req);

    void updateTenant(Long id, TenantSaveReq req);

    void deleteTenant(Long id);
}
