/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.modules.system.service.support;

import cn.ypbin.admin.modules.system.entity.SysUser;
import cn.ypbin.admin.modules.system.entity.SysUserPasswordHistory;
import cn.ypbin.admin.modules.system.mapper.SysUserMapper;
import cn.ypbin.admin.modules.system.mapper.SysUserPasswordHistoryMapper;
import cn.ypbin.admin.modules.system.service.SysConfigService;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.password.PasswordEncoderUtil;
import cn.ypbin.starter.security.password.policy.PasswordCheckResult;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import cn.ypbin.starter.tenant.core.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户账号横切支持：密码策略校验、密码历史、手机号查重。
 *
 * <p>供用户管理与个人中心两个服务共用，保持单一职责；不承载事务边界，事务由调用方声明。</p>
 *
 * @author wenbin
 * @since 2026-08-31
 */
@Component
@RequiredArgsConstructor
public class UserAccountSupport {

    private final SysUserMapper userMapper;
    private final SysUserPasswordHistoryMapper passwordHistoryMapper;
    private final SysConfigService configService;
    private final PasswordValidator passwordValidator;

    /**
     * 按密码策略校验密码复杂度，不通过抛业务异常。
     */
    public void validatePassword(String rawPassword, String username) {
        PasswordCheckResult result = passwordValidator.check(rawPassword, username);
        if (!result.passed()) {
            throw new BusinessException(result.message());
        }
    }

    /**
     * 校验新密码是否与最近 N 次历史密码重复（N 由 PASSWORD_HISTORY_COUNT 控制，0 不校验）。
     */
    public void checkPasswordHistory(Long userId, String rawPassword) {
        int historyCount = configService.getInt("PASSWORD_HISTORY_COUNT", 0);
        if (historyCount <= 0) {
            return;
        }
        List<SysUserPasswordHistory> histories = passwordHistoryMapper.selectList(
            new LambdaQueryWrapper<SysUserPasswordHistory>()
                .eq(SysUserPasswordHistory::getUserId, userId)
                .orderByDesc(SysUserPasswordHistory::getCreateTime)
                .last("LIMIT " + historyCount));
        boolean reused = histories.stream()
            .anyMatch(h -> PasswordEncoderUtil.matches(rawPassword, h.getPassword()));
        if (reused) {
            throw new BusinessException("新密码不能与最近 " + historyCount + " 次使用过的密码相同");
        }
    }

    /**
     * 记录一条历史密码。
     */
    public void recordPasswordHistory(Long userId, String encodedPassword) {
        SysUserPasswordHistory history = new SysUserPasswordHistory();
        history.setUserId(userId);
        history.setPassword(encodedPassword);
        passwordHistoryMapper.insert(history);
    }

    /**
     * 手机号全局唯一查重（手机号跨租户唯一，查重时忽略租户过滤）。
     */
    public void checkPhoneUnique(String phone, Long excludeId) {
        if (phone == null) {
            return;
        }
        boolean exists = TenantContext.executeIgnore(() -> userMapper.exists(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getPhone, phone)
            .ne(excludeId != null, SysUser::getId, excludeId)));
        if (exists) {
            throw new BusinessException("手机号已存在：" + phone);
        }
    }

    /**
     * 规整手机号，空白返回 {@code null}。
     */
    public String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }
}
