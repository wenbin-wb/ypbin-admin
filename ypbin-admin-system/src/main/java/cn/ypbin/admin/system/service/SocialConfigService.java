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

import cn.ypbin.admin.system.model.req.SocialConfigUpdateReq;
import cn.ypbin.admin.system.model.resp.SocialConfigResp;
import java.util.List;
import me.zhyd.oauth.request.AuthRequest;

/**
 * 第三方登录平台配置服务。
 *
 * @author wenbin
 * @since 2026-08-08
 */
public interface SocialConfigService {

    List<SocialConfigResp> listConfigs();

    SocialConfigResp getConfig(String source);

    void updateConfig(String source, SocialConfigUpdateReq req);

    AuthRequest createEnabledRequest(String source);
}
