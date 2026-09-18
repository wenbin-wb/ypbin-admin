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
package cn.ypbin.admin.common.log;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 登录客户端信息数据源（sa-token 会话实现）。
 *
 * <p><strong>三个字段从哪取</strong>：starter 的 {@code LogCollector} 在采集 {@code CLIENT} 项时调用
 * 本端口，把 {@code LogClientInfo} 的三个值原样写入 {@code LogRecord} 的
 * {@code clientId/clientType/authType}。本实现从<strong>登录会话里的 {@link LoginUser}</strong> 取这三个值
 * ——它们由 auth 服务在登录收尾时写入（{@code LoginSupport#completeLogin}），网关签发身份头时
 * <strong>并不包含</strong>客户端信息（starter 的 {@code IdentityHeaders} 只有
 * id/username/tenantId/deptId/roles 五个头），故<strong>不新增任何请求头契约</strong>，直接读既有的登录态。</p>
 *
 * <p><strong>为什么下游服务读得到</strong>：{@code ypbin-common} 统一引入 {@code sa-token-redis-template}，
 * auth/system/ai 共用同一份 Redis 会话存储与同一 {@code sa-token.token-name}（{@code ypbin-common.yaml}），
 * 因此按 loginId 读账号会话在各服务内结果一致；这与 {@code SysUserServiceImpl#listOnlineUsers}
 * 读会话枚举在线用户是同一套机制。</p>
 *
 * <p><strong>代价</strong>：每次记录操作日志会多一次会话读取（Redis GET + 会话反序列化）。次数与
 * {@code @Log} 方法数同阶（一次请求通常一条），不构成 N+1。</p>
 *
 * <p><strong>失败处理</strong>：读会话异常（或会话中登录用户类型不可识别）时记 error 并返回空，
 * 让本条操作日志<strong>照常落库</strong>（只是这三列为空）。刻意不在采集处抛错：starter 的
 * {@code LogAspect} 对采集异常是「丢整条日志」语义，为三个附加字段丢掉整条审计记录得不偿失；
 * 而错误已带完整堆栈暴露，不属于静默降级。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
public class SessionLogClientProvider implements LogClientProvider {

    private static final Logger log = LoggerFactory.getLogger(SessionLogClientProvider.class);

    @Override
    public Optional<LogClientInfo> getCurrentClient() {
        Long userId = IdentityContext.getUserId().orElse(null);
        if (userId == null) {
            // 未登录/无身份头：无可用的登录态，返回空表示"无客户端信息"（不属于失败）
            return Optional.empty();
        }
        LoginUser loginUser;
        try {
            SaSession session = StpUtil.getSessionByLoginId(userId, false);
            if (session == null) {
                return Optional.empty();
            }
            Object value = session.get(UserContext.KEY_LOGIN_USER);
            if (!(value instanceof LoginUser cached)) {
                if (value != null) {
                    log.error("操作日志客户端信息读取失败：会话中的登录用户类型不可识别（期望 {}，实际 {}），"
                        + "sys_log 的 clientId/clientType/authType 将为空；请确认该类型已登记在"
                        + " META-INF/satoken/sa-json-type.list，userId={}",
                        LoginUser.class.getName(), value.getClass().getName(), userId);
                }
                return Optional.empty();
            }
            loginUser = cached;
        } catch (RuntimeException ex) {
            log.error("操作日志客户端信息读取失败（读取 sa-token 登录会话异常），"
                + "本条日志的 clientId/clientType/authType 为空；userId={}", userId, ex);
            return Optional.empty();
        }
        if (!StringUtils.hasText(loginUser.getClientId()) && !StringUtils.hasText(loginUser.getClientType())
            && !StringUtils.hasText(loginUser.getAuthType())) {
            // 登录时未回填客户端信息：字段留空是事实，不臆造取值
            return Optional.empty();
        }
        return Optional.of(new LogClientInfo(loginUser.getClientId(), loginUser.getClientType(),
            loginUser.getAuthType()));
    }
}
