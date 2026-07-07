package com.dwkshop.backend.cart;

import com.dwkshop.backend.auth.AuthInterceptor;
import com.dwkshop.backend.auth.AuthTokenService;
import com.dwkshop.backend.auth.InternalServiceAuthConfig;
import com.dwkshop.backend.cart.dto.CartResponse;
import com.dwkshop.backend.config.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerAuthTest {

    private MockMvc mockMvc;
    private AuthTokenService authTokenService;
    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        authTokenService = new AuthTokenService(new ObjectMapper(), "test-secret", 3600, 3600);
        AuthInterceptor authInterceptor = new AuthInterceptor(
            authTokenService,
            new InternalServiceAuthConfig("dwkshop-local-internal-secret-change-me")
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addInterceptors(authInterceptor)
            .build();
    }

    @Test
    void userCartEndpointRejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/cart/items"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("please login first"));

        verifyNoInteractions(cartService);
    }

    @Test
    void userCartEndpointUsesAuthenticatedPrincipalWhenUserIdParameterIsForged() throws Exception {
        when(cartService.listItems(1L)).thenReturn(new CartResponse(
            1L,
            0,
            0,
            "¥0.00",
            false,
            "Cart is empty",
            0,
            0,
            List.of()
        ));

        mockMvc.perform(get("/api/cart/items")
                .header("Authorization", "Bearer " + userToken())
                .param("userId", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(1));

        verify(cartService).listItems(1L);
        verify(cartService, org.mockito.Mockito.never()).listItems(2L);
    }

    private String userToken() {
        return authTokenService.issue(1L, "buyer", "USER");
    }
}
