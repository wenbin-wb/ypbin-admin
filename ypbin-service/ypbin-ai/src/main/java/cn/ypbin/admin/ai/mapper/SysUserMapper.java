/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.ai.mapper;

import cn.ypbin.admin.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * AI 服务用户查询（一期共享库直读，二期拆库后改 Feign）。
 *
 * @author wenbin
 * @since 2026-09-01
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
