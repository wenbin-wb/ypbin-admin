/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.model.req;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识库公开分享设置请求。
 *
 * @author wenbin
 * @since 2026-08-18
 */
@Data
public class AiShareSettingReq {

    /** 是否启用分享（true 启用并生成/保留令牌；false 关闭并清除分享配置） */
    @NotNull(message = "是否启用分享不能为空")
    private Boolean enabled;

    /** 分享过期时间（NULL=永不过期） */
    private LocalDateTime expireTime;

    /** 访问密码（明文传入，服务端存 SHA-256 哈希；NULL 或空=无需密码） */
    private String password;
}
