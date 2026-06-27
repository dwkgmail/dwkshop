package com.dwkshop.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthInterceptorTest {

    private final AuthTokenService tokenService = new AuthTokenService(new ObjectMapper(), "test-secret", 3600, 3600);
    private final AuthInterceptor interceptor = new AuthInterceptor(
        tokenService,
        new InternalServiceAuthConfig("dwkshop-local-internal-secret-change-me")
    );

    @Test
    void internalRequestsRequireSharedSecret() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/orders/1/aftersale");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(AuthException.class)
            .hasMessage("internal access unauthorized");

        request.addHeader(InternalServiceAuthConfig.INTERNAL_SECRET_HEADER, "dwkshop-local-internal-secret-change-me");
        assertThatCode(() -> interceptor.preHandle(request, response, new Object())).doesNotThrowAnyException();
    }

    @Test
    void publicRequestsRemainUnaffected() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatCode(() -> interceptor.preHandle(request, response, new Object())).doesNotThrowAnyException();
    }

    @Test
    void userTokenCannotBypassAdminAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/orders/1/ship");
        request.addHeader("Authorization", "Bearer " + tokenService.issue(1L, "buyer", "USER"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
            .isInstanceOf(AuthException.class)
            .hasMessage("admin login required");
    }

    @Test
    void adminPermissionAndHighRiskConfirmationAreEnforced() throws Exception {
        HandlerMethod handler = handler("highRiskProductOperation");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest noPermission = new MockHttpServletRequest("POST", "/admin/products/1/off-sale");
        noPermission.addHeader("Authorization", "Bearer " + tokenService.issue(2L, "operator", "ORDER_SERVICE", java.util.Set.of("order:read")));
        assertThatThrownBy(() -> interceptor.preHandle(noPermission, response, handler))
            .isInstanceOf(AuthException.class)
            .hasMessage("admin permission denied");

        MockHttpServletRequest noConfirmation = new MockHttpServletRequest("POST", "/admin/products/1/off-sale");
        noConfirmation.addHeader("Authorization", "Bearer " + tokenService.issue(3L, "operator", "PRODUCT_OPERATOR", java.util.Set.of("product:publish")));
        assertThatThrownBy(() -> interceptor.preHandle(noConfirmation, response, handler))
            .isInstanceOf(AuthException.class)
            .hasMessage("high risk operation requires confirmation and reason");

        MockHttpServletRequest confirmed = new MockHttpServletRequest("POST", "/admin/products/1/off-sale");
        confirmed.addHeader("Authorization", "Bearer " + tokenService.issue(4L, "operator", "PRODUCT_OPERATOR", java.util.Set.of("product:publish")));
        confirmed.addHeader("X-Admin-Confirm", "true");
        confirmed.addHeader("X-Admin-Reason", "test reason");
        assertThatCode(() -> interceptor.preHandle(confirmed, response, handler)).doesNotThrowAnyException();
    }

    @Test
    void expiredAccessTokenCanBeRejectedWhileRefreshTokenIssuesNewPrincipal() throws Exception {
        AuthTokenService expiredAccessService = new AuthTokenService(new ObjectMapper(), "refresh-secret", -1, 3600);
        AuthTokenService refreshService = new AuthTokenService(new ObjectMapper(), "refresh-secret", 3600, 3600);
        String expiredAccessToken = expiredAccessService.issue(7L, "alice", "USER");
        String refreshToken = expiredAccessService.issueRefresh(7L, "alice", "USER");

        Thread.sleep(1100);

        assertThatThrownBy(() -> refreshService.verify(expiredAccessToken))
            .isInstanceOf(AuthException.class);
        assertThatCode(() -> refreshService.verifyRefresh(refreshToken))
            .doesNotThrowAnyException();
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = TestAdminController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new TestAdminController(), method);
    }

    private static class TestAdminController {

        @RequiresPermission("product:publish")
        @RequiresConfirmation
        void highRiskProductOperation() {
        }
    }
}
