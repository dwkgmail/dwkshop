package com.dwkshop.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor(
        new AuthTokenService(new ObjectMapper(), "test-secret", 3600, 3600),
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
}
