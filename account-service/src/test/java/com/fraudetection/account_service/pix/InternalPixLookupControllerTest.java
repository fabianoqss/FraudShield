package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.exceptions.PixLookupNotFoundException;
import com.fraudetection.account_service.security.SecurityConfig;
import com.fraudetection.account_service.security.TokenTypeAuthoritiesConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalPixLookupController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json",
        "jwt.issuer=fraudshield-auth-service"
})
class InternalPixLookupControllerTest {

    private static final UUID USER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PixLookupService pixLookupService;

    @Test
    void resolvesTheLookupOfTheCaller() throws Exception {
        UUID lookupId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(pixLookupService.resolve(lookupId, USER)).thenReturn(accountId);

        mockMvc.perform(get("/internal/pix-keys/lookups/{id}", lookupId).with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationAccountId").value(accountId.toString()));
    }

    @Test
    void unknownExpiredOrForeignLookupIsNotFound() throws Exception {
        UUID lookupId = UUID.randomUUID();
        when(pixLookupService.resolve(lookupId, USER)).thenThrow(new PixLookupNotFoundException());

        mockMvc.perform(get("/internal/pix-keys/lookups/{id}", lookupId).with(userJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedLookupIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/internal/pix-keys/lookups/not-a-uuid").with(userJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresToken() throws Exception {
        mockMvc.perform(get("/internal/pix-keys/lookups/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(pixLookupService);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt() {
        return jwt()
                .jwt(token -> token.subject(USER.toString()).claim("token_type", "user"))
                .authorities(new TokenTypeAuthoritiesConverter());
    }
}
