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

import cn.ypbin.admin.system.entity.SysLog;
import cn.ypbin.admin.system.model.query.LogQuery;
import cn.ypbin.admin.system.model.resp.LogResp;
import cn.ypbin.starter.crud.model.PageResult;
import cn.ypbin.starter.crud.service.BaseService;

/**
 * 系统日志服务（只读）。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SysLogService extends BaseService<SysLog> {

    /**
     * 分页查询日志。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<LogResp> pageLogs(LogQuery query);
}
