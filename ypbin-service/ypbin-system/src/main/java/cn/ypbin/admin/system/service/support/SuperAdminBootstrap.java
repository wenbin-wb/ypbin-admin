/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.support;

import cn.ypbin.admin.common.constant.AdminConstants;
import cn.ypbin.admin.common.support.PwdChangeKeys;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 平台超管初始口令引导：把部署时随机生成的口令写入种子账号。
 *
 * <p><b>为什么需要它</b>：种子 SQL 里的超管口令是仓库内公开的演示值，直接用于生产等于公开密码。
 * 但 bcrypt 哈希无法在 shell 侧可靠生成（部署机不保证有 htpasswd / python-bcrypt），
 * 因此改由应用启动时用自身的 {@link PasswordEncoderUtil} 完成：安装脚本只负责生成随机明文并注入环境变量。</p>
 *
 * <p><b>边界</b>：仅当超管仍处于「强制改密」状态（{@code pwd_change_required=1}）时才写入；
 * 用户自行改密后该标记清零，后续重启绝不会把口令改回去。未注入口令时直接跳过，
 * 保留种子口令并同样要求首登改密（开发/手工部署）。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    /** 内置平台超管用户名（见 deploy/sql/002-data.sql）。 */
    private static final String SUPER_ADMIN_USERNAME = "admin";

    private final SysUserMapper userMapper;

    /** 部署时注入的初始口令；为空表示不接管种子口令。 */
    @Value("${ypbin.security.super-admin.init-password:}")
    private String initPassword;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(initPassword)) {
            return;
        }
        SysUser admin = TenantContext.executeIgnore(() -> userMapper.selectOne(
            new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, SUPER_ADMIN_USERNAME)
                .eq(SysUser::getUserType, AdminConstants.USER_TYPE_PLATFORM)
                .last("LIMIT 1")));
        if (admin == null) {
            // 种子尚未导入（首次启动早于 SQL 初始化）时静默跳过：下次启动会再次尝试
            log.info("未找到平台超管 {}，跳过初始口令设置", SUPER_ADMIN_USERNAME);
            return;
        }
        if (!Integer.valueOf(PwdChangeKeys.REQUIRED_YES).equals(admin.getPwdChangeRequired())) {
            // 已改过密：绝不再覆盖用户自己的口令
            return;
        }
        if (PasswordEncoderUtil.matches(initPassword, admin.getPassword())) {
            // 幂等：口令已是部署配置值（重复启动不重复写库）
            return;
        }
        int updated = TenantContext.executeIgnore(() -> userMapper.update(null,
            new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, admin.getId())
                // CAS：并发启动/用户恰好改密时不动
                .eq(SysUser::getPwdChangeRequired, PwdChangeKeys.REQUIRED_YES)
                .set(SysUser::getPassword, PasswordEncoderUtil.encode(initPassword))
                .set(SysUser::getPwdResetTime, LocalDateTime.now())));
        if (updated == 1) {
            log.info("已按部署配置设置平台超管初始口令（仍要求首登改密）");
        } else {
            log.warn("平台超管初始口令未更新：状态已变化或存在并发修改，本次跳过");
        }
    }
}
