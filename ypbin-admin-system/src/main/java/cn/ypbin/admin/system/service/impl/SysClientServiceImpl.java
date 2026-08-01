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

import cn.ypbin.admin.system.entity.SysClient;
import cn.ypbin.admin.system.mapper.SysClientMapper;
import cn.ypbin.admin.system.service.SysClientService;
import cn.ypbin.starter.crud.service.BaseServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 登录客户端服务实现。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Service
public class SysClientServiceImpl extends BaseServiceImpl<SysClientMapper, SysClient> implements SysClientService {
}
