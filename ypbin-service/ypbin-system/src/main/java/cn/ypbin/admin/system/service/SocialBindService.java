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

import cn.ypbin.admin.system.entity.SysUserSocial;
import java.util.List;

/**
 * 用户-第三方平台绑定服务。
 *
 * <p>绑定数据存于共享库 sys_user_social（全局表，不隔离租户）。auth-svc 经
 * {@code ISystemClient} Feign 调用本服务完成绑定查询/新增/解绑，不直连共享库。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public interface SocialBindService {

    /**
     * 按平台与 openId 查绑定（第三方登录用）。
     *
     * @param platform 平台标识
     * @param openId   第三方 openId
     * @return 绑定记录，无则返回 {@code null}
     */
    SysUserSocial getByPlatformAndOpenId(String platform, String openId);

    /**
     * 用户是否已绑定指定平台。
     *
     * @param userId   用户 ID
     * @param platform 平台标识
     * @return 是否已绑定
     */
    boolean isUserBound(Long userId, String platform);

    /**
     * 按平台与 openId 是否已绑定其他用户。
     *
     * @param platform 平台标识
     * @param openId   第三方 openId
     * @return 是否已被绑定
     */
    boolean isAccountBound(String platform, String openId);

    /**
     * 新增绑定。
     *
     * @param social 绑定记录
     */
    void save(SysUserSocial social);

    /**
     * 解绑（按用户+平台）。
     *
     * @param userId   用户 ID
     * @param platform 平台标识
     */
    void unbind(Long userId, String platform);

    /**
     * 用户已绑定的平台列表。
     *
     * @param userId 用户 ID
     * @return 绑定列表，无则返回空集合
     */
    List<SysUserSocial> listByUserId(Long userId);
}
