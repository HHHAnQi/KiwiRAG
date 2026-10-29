package com.xxx.ragdoc.interfaces.rest.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.xxx.ragdoc.application.auth.AuthContext;
import com.xxx.ragdoc.application.auth.AuthProperties;
import com.xxx.ragdoc.application.auth.AuthenticationPort;
import com.xxx.ragdoc.domain.auth.Principal;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Task 11 P0: {@link AuthFilter} fail-closed 单元验证。
 *
 * <p>覆盖任务文档 §6.1 全部场景 (mock AuthenticationPort, 不调真 DB):
 *
 * <ul>
 *   <li>无 token → 401, 不调 Controller
 *   <li>未知 token → 401, 不调 Controller
 *   <li>非法 token 格式 → 401
 *   <li>有效 token → 200, AuthContext 被设为真实 Principal
 *   <li>health check 无 token → 200 (allowlist)
 *   <li>请求结束后 AuthContext 被清 (ThreadLocal)
 *   <li>DB 异常 → 500 (不 fallback)
 *   <li>dev-default-token + flag on → 200 + DEFAULT_PRINCIPAL
 *   <li>dev-default-token + flag off → 401
 * </ul>
 */
@DisplayName("Task 11 P0 AuthFilter fail-closed")
class AuthFilterFailClosedTest {

    private AuthenticationPort authenticationPort;
    private AuthProperties props;
    private AuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setup() {
        authenticationPort = mock(AuthenticationPort.class);
        props = new AuthProperties(mock(org.springframework.core.env.Environment.class));
        props.setDevDefaultPrincipalEnabled(false); // 默认关闭, 测 deny-default
        filter = new AuthFilter(authenticationPort, props);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void clear() {
        AuthContext.clear();
    }

    private MockHttpServletRequest req(String path, String authHeader) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", path);
        if (authHeader != null) r.addHeader("Authorization", authHeader);
        return r;
    }

    private int status(MockHttpServletResponse resp) {
        return resp.getStatus();
    }

    @Test
    @DisplayName("无 token 访问 /api/v1/chat → 401, 不调 Controller")
    void noTokenBlocks() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/chat", null), resp, chain);
        assertThat(status(resp)).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("未知 token → 401, 不调 Controller")
    void unknownTokenBlocks() throws Exception {
        when(authenticationPort.authenticate("ghost-token")).thenReturn(Optional.empty());
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/chat", "Bearer ghost-token"), resp, chain);
        assertThat(status(resp)).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("非法 Authorization header 格式 → 401")
    void malformedHeaderBlocks() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/chat", "Basic abc"), resp, chain);
        assertThat(status(resp)).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("有效 token → 进入 Controller + AuthContext 写入真实 Principal")
    void validTokenPasses() throws Exception {
        Principal principal =
                new Principal(
                        "tenantA",
                        "userA",
                        java.util.Set.of("role:default", "role:user"),
                        "good-token");
        when(authenticationPort.authenticate("good-token")).thenReturn(Optional.of(principal));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/chat", "Bearer good-token"), resp, chain);

        assertThat(status(resp)).isEqualTo(200);
        verify(chain).doFilter(any(), any());
        // 链路进行中 AuthContext 应是真实 Principal
        // (filter finally 已 cleared, 所以不能在 after 验; verify chain.doFilter 时
        // 在 doAnswer 内验证 — 见 concurrentIsolation 测试)
    }

    @Test
    @DisplayName("allowlist 路径 /actuator/health 无 token → 200")
    void healthCheckNoToken() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/actuator/health", null), resp, chain);
        assertThat(status(resp)).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("DB 异常 → 500 (不 fallback 默认主体)")
    void dbExceptionReturns500NotFallback() throws Exception {
        when(authenticationPort.authenticate("any-token"))
                .thenThrow(new RuntimeException("MySQL down"));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/chat", "Bearer any-token"), resp, chain);
        assertThat(status(resp)).isEqualTo(500);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("dev-default-token + flag off → 401 (默认配置)")
    void devDefaultToken_blockedWhenFlagOff() throws Exception {
        // 默认 props.devDefaultPrincipalEnabled=false
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/chat", "Bearer dev-default-token"), resp, chain);
        assertThat(status(resp)).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("dev-default-token + flag on → 200 + 写入 DEFAULT_PRINCIPAL")
    void devDefaultToken_passWhenFlagOn() throws Exception {
        props.setDevDefaultPrincipalEnabled(true);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        // doAnswer 在 chain 阶段验证 AuthContext.currentPrincipal()
        doAnswer(
                        inv -> {
                            Principal p = AuthContext.currentPrincipal();
                            org.assertj.core.api.Assertions.assertThat(p.userId())
                                    .as("DEFAULT_PRINCIPAL.userId='dev'")
                                    .isEqualTo("dev");
                            return null;
                        })
                .when(chain)
                .doFilter(any(), any());
        filter.doFilter(req("/api/v1/chat", "Bearer dev-default-token"), resp, chain);
        assertThat(status(resp)).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("请求结束 (无论成功/失败) 后 AuthContext 被清回 DEFAULT (防旧 principal 串号)")
    void authContextClearedAfterRequest() throws Exception {
        Principal principal = new Principal("t1", "u1", java.util.Set.of("role:default"), "t");
        when(authenticationPort.authenticate("t")).thenReturn(Optional.of(principal));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req("/api/v1/chat", "Bearer t"), resp, chain);

        // finally 已清; ThreadLocal 是 null, 回到 fallback DEFAULT_PRINCIPAL
        // 关键不变量: 之前的 u1 principal 不再在 ThreadLocal 中 (防 tomcat 重用线程串号)
        Principal p = AuthContext.currentPrincipal();
        assertThat(p.userId()).as("ThreadLocal 已清, fallback 到 DEFAULT").isEqualTo("dev");
    }

    @Nested
    @DisplayName("并发身份隔离: ThreadLocal 不串")
    class Concurrency {
        @Test
        @DisplayName("两线程并发不同 token → 各自 AuthContext 独立, 不串号")
        void threadLocalNoBleed() throws Exception {
            Principal a = new Principal("tA", "userA", java.util.Set.of("role:default"), "tokA");
            Principal b = new Principal("tB", "userB", java.util.Set.of("role:default"), "tokB");

            when(authenticationPort.authenticate("tokA")).thenReturn(Optional.of(a));
            when(authenticationPort.authenticate("tokB")).thenReturn(Optional.of(b));

            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(2);
            java.util.List<String> errors =
                    java.util.Collections.synchronizedList(new java.util.ArrayList<>());

            Runnable ra =
                    () -> {
                        try {
                            MockHttpServletResponse resp = new MockHttpServletResponse();
                            FilterChain c = mock(FilterChain.class);
                            doAnswer(
                                            inv -> {
                                                String userId =
                                                        AuthContext.currentPrincipal().userId();
                                                if (!"userA".equals(userId)) {
                                                    errors.add("threadA saw " + userId);
                                                }
                                                return null;
                                            })
                                    .when(c)
                                    .doFilter(any(), any());
                            filter.doFilter(req("/api/v1/chat", "Bearer tokA"), resp, c);
                        } catch (Exception e) {
                            errors.add("threadA exc " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    };
            Runnable rb =
                    () -> {
                        try {
                            MockHttpServletResponse resp = new MockHttpServletResponse();
                            FilterChain c = mock(FilterChain.class);
                            doAnswer(
                                            inv -> {
                                                String userId =
                                                        AuthContext.currentPrincipal().userId();
                                                if (!"userB".equals(userId)) {
                                                    errors.add("threadB saw " + userId);
                                                }
                                                return null;
                                            })
                                    .when(c)
                                    .doFilter(any(), any());
                            filter.doFilter(req("/api/v1/chat", "Bearer tokB"), resp, c);
                        } catch (Exception e) {
                            errors.add("threadB exc " + e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    };

            new Thread(ra).start();
            new Thread(rb).start();
            latch.await();
            assertThat(errors).isEmpty();
        }
    }
}
