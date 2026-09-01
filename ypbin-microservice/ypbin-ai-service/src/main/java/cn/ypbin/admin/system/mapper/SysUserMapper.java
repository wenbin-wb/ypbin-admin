/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户 Mapper。
 *
 * <p>共享类：复制自单体版 ypbin-admin-system（与 system-svc 同源），
 * 后续抽公共库时统一收敛到 common-api，勿单侧修改。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
