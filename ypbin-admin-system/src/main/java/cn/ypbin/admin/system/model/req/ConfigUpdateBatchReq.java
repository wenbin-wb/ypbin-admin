/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.req;

import java.util.Map;
import lombok.Data;

/**
 * 系统参数批量更新请求。按 key-value 批量保存同一分组的配置项。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Data
public class ConfigUpdateBatchReq {

    /** 参数键值对（configKey -> configValue） */
    private Map<String, String> configs;
}
