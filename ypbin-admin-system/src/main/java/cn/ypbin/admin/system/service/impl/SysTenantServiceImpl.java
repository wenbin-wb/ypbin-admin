/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import cn.ypbin.admin.system.entity.SysTenant;
import cn.ypbin.admin.system.mapper.SysTenantMapper;
import cn.ypbin.admin.system.service.SysTenantService;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 租户服务实现。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Service
public class SysTenantServiceImpl extends BaseServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {
}
