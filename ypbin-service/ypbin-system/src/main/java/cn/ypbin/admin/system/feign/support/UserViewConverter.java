/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.feign.support;

import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserSocial;
import cn.ypbin.admin.system.model.dto.SysUserDto;
import cn.ypbin.admin.system.model.dto.SysUserSocialDto;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 用户/第三方绑定：实体 → 跨服务只读视图的唯一转换点。
 *
 * <p><b>为什么需要它</b>：实体继承 {@code BaseEntity}/{@code TenantBaseEntity}，若让 Feign 契约或缓存
 * 对外暴露实体类型，则 auth/ai 这类<b>无数据源</b>的服务会被迫传递依赖 {@code ypbin-starter-data}
 * （MyBatis-Plus），违反本仓「auth/ai 不直连共享库」的约定——不仅源码引用会受影响，
 * 连 Mockito 为 {@code ISystemClient} 生成 mock 都会因签名里的实体无法加载而失败。</p>
 *
 * <p><b>字段口径</b>：视图字段与实体<b>逐一同名同类型</b>，不做任何改名映射；
 * 转换只做「投影」，不改变任何语义。转换逻辑集中在本类，避免多点各写一份而漂移。</p>
 *
 * <p><b>为什么本类放在 service 模块而不是 api 模块</b>：它必须引用实体类型，而实体继承
 * {@code BaseEntity}（`starter-data`）。若把它放进 `ypbin-system-api`，则已排除 `starter-data` 的
 * auth 会在 classpath 上拿到一个「能加载、一旦解析方法就 `NoClassDefFoundError`」的类——
 * 那是潜伏的踩雷点。投影只在 system 侧（`SystemClientImpl`）使用，故与它同模块就近放置。</p>
 *
 * @author wenbin
 * @since 2026-09-18
 */
public final class UserViewConverter {

    private UserViewConverter() {
    }

    /**
     * 用户实体 → 用户视图（不含密码）。
     *
     * @param user 用户实体，可为空
     * @return 用户视图；入参为空时返回空
     */
    public static @Nullable SysUserDto toDto(@Nullable SysUser user) {
        if (user == null) {
            return null;
        }
        SysUserDto dto = new SysUserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRealName(user.getRealName());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setPhone(user.getPhone());
        dto.setTenantId(user.getTenantId());
        dto.setDeptId(user.getDeptId());
        dto.setStatus(user.getStatus());
        return dto;
    }

    /**
     * 第三方绑定实体 → 绑定视图（不含 accessToken）。
     *
     * @param social 绑定实体，可为空
     * @return 绑定视图；入参为空时返回空
     */
    public static @Nullable SysUserSocialDto toDto(@Nullable SysUserSocial social) {
        if (social == null) {
            return null;
        }
        SysUserSocialDto dto = new SysUserSocialDto();
        dto.setUserId(social.getUserId());
        dto.setPlatform(social.getPlatform());
        dto.setOpenId(social.getOpenId());
        return dto;
    }

    /**
     * 用户实体列表 → 用户视图列表；入参为空时返回空集合（集合禁返回 null）。
     */
    public static List<SysUserDto> toUserDtoList(@Nullable List<SysUser> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<SysUserDto> result = new ArrayList<>(users.size());
        for (SysUser user : users) {
            result.add(toDto(user));
        }
        return result;
    }

    /**
     * 绑定实体列表 → 绑定视图列表；入参为空时返回空集合。
     */
    public static List<SysUserSocialDto> toSocialDtoList(@Nullable List<SysUserSocial> socials) {
        if (socials == null || socials.isEmpty()) {
            return List.of();
        }
        List<SysUserSocialDto> result = new ArrayList<>(socials.size());
        for (SysUserSocial social : socials) {
            result.add(toDto(social));
        }
        return result;
    }
}
