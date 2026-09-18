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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.log.support.LogCollector;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import tools.jackson.databind.ObjectMapper;

/**
 * 操作日志客户端信息数据源测试。
 *
 * <p>钉住三件事：① 三个字段的来源是登录会话里的 {@code LoginUser}（不是请求头）；② 用真实
 * {@code LogCollector} 验证这三个值确实写进了 {@code LogRecord} 的
 * {@code clientId/clientType/authType}；③ 未登录/会话缺失/会话反序列化异常时返回空而不是抛错，
 * 保证本条操作日志仍然落库（只是三列为空）。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class SessionLogClientProviderTest {

    private static final Long USER_ID = 42L;

    private final SessionLogClientProvider provider = new SessionLogClientProvider();

    @AfterEach
    void clearIdentity() {
        IdentityContext.clear();
    }

    @Test
    @DisplayName("未登录（无身份头）时不读会话，返回空")
    void shouldReturnEmptyWhenNotLoggedIn() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            assertThat(provider.getCurrentClient()).isEmpty();
            stpUtil.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("登录会话中的 clientId/clientType/authType 原样返回")
    void shouldReadClientInfoFromLoginSession() {
        login(USER_ID);

        SaSession session = sessionWith(loginUser("web-admin", "WEB", "ACCOUNT"));
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getSessionByLoginId(USER_ID, false)).thenReturn(session);

            Optional<LogClientProvider.LogClientInfo> client = provider.getCurrentClient();

            assertThat(client).isPresent();
            assertThat(client.get().clientId()).isEqualTo("web-admin");
            assertThat(client.get().clientType()).isEqualTo("WEB");
            assertThat(client.get().authType()).isEqualTo("ACCOUNT");
        }
    }

    @Test
    @DisplayName("会话中三项都为空（登录时未回填）时返回空，不臆造取值")
    void shouldReturnEmptyWhenSessionHasNoClientInfo() {
        login(USER_ID);

        SaSession session = sessionWith(loginUser(null, null, null));
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getSessionByLoginId(USER_ID, false)).thenReturn(session);

            assertThat(provider.getCurrentClient()).isEmpty();
        }
    }

    @Test
    @DisplayName("会话不存在时返回空")
    void shouldReturnEmptyWhenSessionMissing() {
        login(USER_ID);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getSessionByLoginId(USER_ID, false)).thenReturn(null);

            assertThat(provider.getCurrentClient()).isEmpty();
        }
    }

    @Test
    @DisplayName("会话中的登录用户类型不可识别时返回空（不抛错，日志仍落库）")
    void shouldReturnEmptyWhenSessionValueTypeUnrecognized() {
        login(USER_ID);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            SaSession session = mock(SaSession.class);
            stpUtil.when(() -> StpUtil.getSessionByLoginId(USER_ID, false)).thenReturn(session);
            when(session.get(UserContext.KEY_LOGIN_USER)).thenReturn(Map.of("username", "tester"));

            assertThat(provider.getCurrentClient()).isEmpty();
        }
    }

    @Test
    @DisplayName("读会话抛异常时返回空并留痕，绝不把整条操作日志带走")
    void shouldReturnEmptyWhenSessionReadFails() {
        login(USER_ID);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getSessionByLoginId(USER_ID, false))
                .thenThrow(new IllegalStateException("Redis 不可用"));

            assertThat(provider.getCurrentClient()).isEmpty();
        }
    }

    @Test
    @DisplayName("三列最终写进 LogRecord（用真实 LogCollector 验证取值来源）")
    void shouldFillClientColumnsThroughLogCollector() {
        login(USER_ID);
        LogRecord record = new LogRecord();

        SaSession session = sessionWith(loginUser("web-admin", "WEB", "ACCOUNT"));
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(() -> StpUtil.getSessionByLoginId(USER_ID, false)).thenReturn(session);

            LogUserProvider userProvider = () -> Optional.of(USER_ID);
            new LogCollector(userProvider, provider, ip -> null, new ObjectMapper())
                .collect(record, Set.of(Include.CLIENT), new Object[] {}, null, null);
        }

        assertThat(record.getClientId()).isEqualTo("web-admin");
        assertThat(record.getClientType()).isEqualTo("WEB");
        assertThat(record.getAuthType()).isEqualTo("ACCOUNT");
    }

    @Test
    @DisplayName("未登录时 LogRecord 三列保持为空（不写假值）")
    void shouldLeaveClientColumnsNullWhenNotLoggedIn() {
        LogRecord record = new LogRecord();

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            new LogCollector(() -> Optional.empty(), provider, ip -> null, new ObjectMapper())
                .collect(record, Set.of(Include.CLIENT), new Object[] {}, null, null);
            // 未登录连会话都不读
            stpUtil.verifyNoInteractions();
        }

        assertThat(record.getClientId()).isNull();
        assertThat(record.getClientType()).isNull();
        assertThat(record.getAuthType()).isNull();
    }

    private void login(Long userId) {
        IdentityContext.setLoginUser(new LoginUser(userId, "tester"));
    }

    private LoginUser loginUser(String clientId, String clientType, String authType) {
        LoginUser loginUser = new LoginUser(USER_ID, "tester");
        loginUser.setClientId(clientId);
        loginUser.setClientType(clientType);
        loginUser.setAuthType(authType);
        return loginUser;
    }

    private SaSession sessionWith(LoginUser loginUser) {
        SaSession session = mock(SaSession.class);
        when(session.get(UserContext.KEY_LOGIN_USER)).thenReturn(loginUser);
        return session;
    }
}
