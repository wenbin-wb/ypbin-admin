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

/**
 * 第三方登录平台配置服务。
 *
 * <p>平台凭据存于系统参数（sys_config 的 SOCIAL_* 键，social 分组），由本服务提供
 * 专用的查询/修改入口，普通参数接口已拒绝维护 social 分组（见 SysConfigServiceImpl）。
 * 配置提交后发布 {@code SocialConfigChangedEvent} 同步平台注册表。</p>
 *
 * @author wenbin
 * @since 2026-08-08
 */
public interface SocialConfigService {

    /**
     * 查询全部平台配置（不含密钥明文）。
     *
     * @return 平台配置列表
     */
    List<SocialConfigResp> listConfigs();

    /**
     * 查询指定平台配置（不含密钥明文）。
     *
     * @param source 平台标识
     * @return 平台配置
     */
    SocialConfigResp getConfig(String source);

    /**
     * 更新指定平台配置。
     *
     * @param source 平台标识
     * @param req    配置内容
     */
    void updateConfig(String source, SocialConfigUpdateReq req);
}
