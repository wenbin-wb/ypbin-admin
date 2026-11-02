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
package cn.ypbin.admin.common.support;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.UserContext;

/**
 * 当前租户上下文读取（业务服务共用）。
 *
 * <p>各业务实现原先各自写一份「{@code UserContext.getTenantId().orElseThrow(...)}」，
 * 异常文案还分叉成「无法确定当前租户」与「无法获取当前租户上下文」两种。收敛到此处后，
 * 取不到租户时的失败语义只有一处定义，新增调用方不会再生出第三种文案。</p>
 *
 * @author wenbin
 * @since 2026-11-02
 */
public final class TenantSupport {

    private TenantSupport() {
    }

    /**
     * 当前登录用户的租户 ID；无租户上下文时显式失败（禁止静默回退默认租户）。
     *
     * @return 租户 ID
     */
    public static Long currentTenantId() {
        return UserContext.getTenantId()
            .orElseThrow(() -> new BusinessException("无法确定当前租户"));
    }
}
