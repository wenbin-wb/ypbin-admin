/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.model.req;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
/**
 * 开放应用新增/编辑请求。accessKey/secretKey 由服务端生成，不接收外部输入。
 *
 * @author wenbin
 * @since 2026-08-02
 */
@Getter
@Setter
public class SysAppSaveReq {

    /** 应用名称 */
    @NotBlank(message = "应用名称不能为空")
    private String appName;

    /** 过期时间，为空表示永不过期 */
    private LocalDateTime expireTime;

    /** 是否启用 */
    private Integer enabled;
}
