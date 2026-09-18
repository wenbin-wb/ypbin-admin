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
package cn.ypbin.admin.system.service.support;

import cn.ypbin.admin.system.entity.SysUserPasswordHistory;
import cn.ypbin.admin.system.mapper.SysUserMapper;
import cn.ypbin.admin.system.mapper.SysUserPasswordHistoryMapper;
import cn.ypbin.admin.system.service.SysConfigService;
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
 * 用户账号横切支持：密码策略校验、密码历史、用户名与手机号全局查重。
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
     * 手机号全局唯一查重（手机号跨租户唯一）。
     *
     * <p>与用户名查重同源，<b>必须在数据范围之外查</b>：{@code updateUser} 带 {@code @DataPermission}，
     * 若用内置 {@code exists} 查重，跨部门重号查不到，校验「通过」后由数据库唯一键抛原始 SQL 错误。
     * 两道过滤分别由 {@code TenantContext.executeIgnore}（租户）与 Mapper 语句上的
     * {@code @InterceptorIgnore(dataPermission = "true")}（数据权限）关闭——{@code TenantContext}
     * 关不掉数据权限，详见 {@code SysUserMapper#countByPhoneGlobal}。</p>
     *
     * @param phone     手机号（可为 {@code null}，为 {@code null} 时不校验）
     * @param excludeId 需排除的用户 ID（编辑时传自身 ID，新增传 {@code null}）
     */
    public void checkPhoneUnique(String phone, Long excludeId) {
        if (phone == null) {
            return;
        }
        long duplicated = TenantContext.executeIgnore(
            () -> userMapper.countByPhoneGlobal(phone, excludeId));
        if (duplicated > 0) {
            throw new BusinessException("手机号已存在：" + phone);
        }
    }

    /**
     * 用户名全局唯一查重（{@code uk_username} 是不带 tenant_id 的全局唯一键）。
     *
     * <p><b>必须在数据范围之外查</b>：调用方 {@code SysUserServiceImpl#updateUser} 带
     * {@code @DataPermission}，其数据范围按部门过滤。查重若落在该范围内，<b>跨部门重名查不到</b>，
     * 校验「通过」后由数据库唯一键抛原始 SQL 错误——是显式失败但不是业务错误，提示不友好。
     * 两道过滤的关闭方式：租户过滤由本方法内的 {@code TenantContext.executeIgnore} 关闭；
     * 数据权限由 Mapper 语句上的 {@code @InterceptorIgnore(dataPermission = "true")} 关闭
     * （{@code TenantContext} 关不掉数据权限，详见 {@code SysUserMapper#countByUsernameGlobal}）。
     * 两者缺一不可，改这里时务必连带确认另一处仍在。</p>
     *
     * @param username  用户名
     * @param excludeId 需排除的用户 ID（编辑时传自身 ID，新增传 {@code null}）
     */
    public void checkUsernameUnique(String username, Long excludeId) {
        long duplicated = TenantContext.executeIgnore(
            () -> userMapper.countByUsernameGlobal(username, excludeId));
        if (duplicated > 0) {
            throw new BusinessException("用户名已存在：" + username);
        }
    }

    /**
     * 规整手机号，空白返回 {@code null}。
     */
    public String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }
}
