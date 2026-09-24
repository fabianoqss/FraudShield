package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.dto.PixKeyLookupResponse;
import com.fraudetection.account_service.pix.dto.PixKeyResponse;
import com.fraudetection.account_service.pix.exceptions.InactiveAccountException;
import com.fraudetection.account_service.pix.exceptions.PixKeyAlreadyRegisteredException;
import com.fraudetection.account_service.pix.exceptions.PixKeyLimitReachedException;
import com.fraudetection.account_service.pix.exceptions.PixKeyNotFoundException;
import com.fraudetection.account_service.pix.exceptions.PixLookupRateLimitedException;
import com.fraudetection.account_service.security.SecurityConfig;
import com.fraudetection.account_service.security.TokenTypeAuthoritiesConverter;
import com.fraudetection.account_service.services.exceptions.AccountAccessDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PixKeyController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json",
        "jwt.issuer=fraudshield-auth-service"
})
class PixKeyControllerTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PixKeyService pixKeyService;

    @MockitoBean
    private PixLookupService pixLookupService;

    @Test
    void registerRequiresToken() throws Exception {
        mockMvc.perform(register("CPF")).andExpect(status().isUnauthorized());
        verifyNoInteractions(pixKeyService);
    }

    @Test
    void registerReturnsCreatedKey() throws Exception {
        when(pixKeyService.register(ACCOUNT, USER, PixKeyType.CPF)).thenReturn(
                new PixKeyResponse(UUID.randomUUID(), PixKeyType.CPF, "52998224725", ACCOUNT, LocalDateTime.now()));

        mockMvc.perform(register("CPF").with(userJwt()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CPF"))
                .andExpect(jsonPath("$.value").value("52998224725"))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT.toString()));
    }

    @Test
    void unknownKeyTypeIsBadRequest() throws Exception {
        mockMvc.perform(register("PHONE").with(userJwt())).andExpect(status().isBadRequest());
        verifyNoInteractions(pixKeyService);
    }

    @Test
    void missingKeyTypeIsBadRequest() throws Exception {
        mockMvc.perform(post("/accounts/{id}/pix-keys", ACCOUNT).with(userJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").exists());
    }

    @Test
    void registerErrorsMapToTheirStatus() throws Exception {
        when(pixKeyService.register(ACCOUNT, USER, PixKeyType.CPF)).thenThrow(new PixKeyAlreadyRegisteredException());
        mockMvc.perform(register("CPF").with(userJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This PIX key is already registered"));

        when(pixKeyService.register(ACCOUNT, USER, PixKeyType.EMAIL)).thenThrow(new PixKeyLimitReachedException());
        mockMvc.perform(register("EMAIL").with(userJwt())).andExpect(status().is(422));

        when(pixKeyService.register(ACCOUNT, USER, PixKeyType.RANDOM)).thenThrow(new InactiveAccountException(ACCOUNT));
        mockMvc.perform(register("RANDOM").with(userJwt())).andExpect(status().is(422));
    }

    @Test
    void anotherUsersAccountIsForbidden() throws Exception {
        when(pixKeyService.list(ACCOUNT, USER)).thenThrow(new AccountAccessDeniedException("Account does not belong to the requesting user"));

        mockMvc.perform(get("/accounts/{id}/pix-keys", ACCOUNT).with(userJwt())).andExpect(status().isForbidden());
    }

    @Test
    void listReturnsKeys() throws Exception {
        when(pixKeyService.list(ACCOUNT, USER)).thenReturn(List.of(
                new PixKeyResponse(UUID.randomUUID(), PixKeyType.EMAIL, "ana@example.com", ACCOUNT, LocalDateTime.now())));

        mockMvc.perform(get("/accounts/{id}/pix-keys", ACCOUNT).with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("ana@example.com"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(delete("/accounts/{id}/pix-keys/{keyId}", ACCOUNT, keyId).with(userJwt()))
                .andExpect(status().isNoContent());
        verify(pixKeyService).delete(ACCOUNT, keyId, USER);
    }

    @Test
    void deleteOfUnknownKeyIsNotFound() throws Exception {
        UUID keyId = UUID.randomUUID();
        doThrow(new PixKeyNotFoundException()).when(pixKeyService).delete(ACCOUNT, keyId, USER);

        mockMvc.perform(delete("/accounts/{id}/pix-keys/{keyId}", ACCOUNT, keyId).with(userJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("PIX key not found"));
    }

    @Test
    void lookupReturnsRecipientForConfirmation() throws Exception {
        UUID lookupId = UUID.randomUUID();
        when(pixLookupService.lookup(USER, "ana@example.com")).thenReturn(new PixKeyLookupResponse(
                lookupId, "Ana Souza", "***.982.247-**", PixKeyType.EMAIL, Instant.parse("2026-09-24T12:05:00Z")));

        mockMvc.perform(lookup("ana@example.com").with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupId").value(lookupId.toString()))
                .andExpect(jsonPath("$.recipientName").value("Ana Souza"))
                .andExpect(jsonPath("$.maskedCpf").value("***.982.247-**"))
                .andExpect(jsonPath("$.accountId").doesNotExist());
    }

    @Test
    void lookupRequiresToken() throws Exception {
        mockMvc.perform(lookup("ana@example.com")).andExpect(status().isUnauthorized());
        verifyNoInteractions(pixLookupService);
    }

    @Test
    void blankLookupKeyIsBadRequest() throws Exception {
        mockMvc.perform(lookup(" ").with(userJwt())).andExpect(status().isBadRequest());
        verifyNoInteractions(pixLookupService);
    }

    @Test
    void rateLimitedLookupReturns429WithRetryAfter() throws Exception {
        when(pixLookupService.lookup(USER, "ana@example.com")).thenThrow(new PixLookupRateLimitedException(15));

        mockMvc.perform(lookup("ana@example.com").with(userJwt()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "15"));
    }

    @Test
    void redisOutageOnLookupIsServiceUnavailable() throws Exception {
        when(pixLookupService.lookup(USER, "ana@example.com"))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        mockMvc.perform(lookup("ana@example.com").with(userJwt()))
                .andExpect(status().isServiceUnavailable());
    }

    private static MockHttpServletRequestBuilder lookup(String key) {
        return post("/accounts/pix-keys/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"" + key + "\"}");
    }

    private static MockHttpServletRequestBuilder register(String type) {
        return post("/accounts/{id}/pix-keys", ACCOUNT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\"}");
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt() {
        return jwt()
                .jwt(token -> token.subject(USER.toString()).claim("token_type", "user"))
                .authorities(new TokenTypeAuthoritiesConverter());
    }
}
