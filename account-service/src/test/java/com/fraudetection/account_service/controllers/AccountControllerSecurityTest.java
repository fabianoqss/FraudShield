package com.fraudetection.account_service.controllers;

import com.fraudetection.account_service.dto.response.BalanceResponse;
import com.fraudetection.account_service.security.SecurityConfig;
import com.fraudetection.account_service.security.TokenTypeAuthoritiesConverter;
import com.fraudetection.account_service.services.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json",
        "jwt.issuer=fraudshield-auth-service"
})
class AccountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/accounts/{id}/balance", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid bearer token"));
    }

    @Test
    void userHeaderAloneNoLongerAuthenticates() throws Exception {
        mockMvc.perform(get("/accounts/{id}/balance", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accountService);
    }

    @Test
    void malformedBearerTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/accounts/{id}/balance", UUID.randomUUID())
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenUsesSubjectAsRequestingUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(accountService.getBalance(accountId, userId)).thenReturn(
                new BalanceResponse("Ana Souza", accountId, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN));

        mockMvc.perform(get("/accounts/{id}/balance", accountId)
                        .with(userJwt(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));

        verify(accountService).getBalance(accountId, userId);
    }

    @Test
    void depositStaysPublic() throws Exception {
        when(accountService.depositByPixKey(any())).thenReturn(
                new BalanceResponse("Ana Souza", UUID.randomUUID(), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN));

        mockMvc.perform(post("/accounts/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pixKey":"ana@example.com","amount":10}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void serviceTokenCannotCallUserRoutes() throws Exception {
        mockMvc.perform(get("/accounts/{id}/balance", UUID.randomUUID()).with(jwt()
                        .jwt(token -> token.subject("account-service").claim("token_type", "service").claim("scope", "users:lookup"))
                        .authorities(new TokenTypeAuthoritiesConverter())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        verifyNoInteractions(accountService);
    }


    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(UUID userId) {
        return jwt()
                .jwt(token -> token.subject(userId.toString()).claim("token_type", "user"))
                .authorities(new TokenTypeAuthoritiesConverter());
    }
}
