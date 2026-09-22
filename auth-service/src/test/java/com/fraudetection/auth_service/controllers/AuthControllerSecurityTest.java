package com.fraudetection.auth_service.controllers;

import com.fraudetection.auth_service.dto.response.ServiceTokenResponse;
import com.fraudetection.auth_service.dto.response.UserLookupResponse;
import com.fraudetection.auth_service.entities.User;
import com.fraudetection.auth_service.security.JwtKeyConfig;
import com.fraudetection.auth_service.security.JwtService;
import com.fraudetection.auth_service.security.SecurityConfig;
import com.fraudetection.auth_service.services.AuthService;
import com.fraudetection.auth_service.services.ServiceTokenService;
import com.fraudetection.auth_service.services.exceptions.InvalidClientCredentialsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, JwksController.class})
@Import({SecurityConfig.class, JwtKeyConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.issuer=fraudshield-auth-service",
        "jwt.expiration-ms=900000",
        "jwt.service-token-expiration-ms=300000"
})
class AuthControllerSecurityTest {

    private static final String CHANGE_PASSWORD_BODY = """
            {"currentPassword":"Old@12345","newPassword":"New@12345","confirmPassword":"New@12345"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ServiceTokenService serviceTokenService;

    @Test
    void jwksIsPublicAndExposesOnlyThePublicKey() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist());
    }

    @Test
    void userHeaderAloneNoLongerAuthenticates() throws Exception {
        mockMvc.perform(put("/auth/password")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_PASSWORD_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing or invalid bearer token"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = userToken();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(put("/auth/password")
                        .header("Authorization", "Bearer " + tampered)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_PASSWORD_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validUserTokenCanChangePassword() throws Exception {
        mockMvc.perform(put("/auth/password")
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_PASSWORD_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void serviceTokenCannotChangePassword() throws Exception {
        String serviceToken = jwtService.generateServiceToken("account-service", List.of("users:lookup"));

        mockMvc.perform(put("/auth/password")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CHANGE_PASSWORD_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookupWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/auth/users/lookup").param("email", "ana@example.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookupWithUserTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/auth/users/lookup")
                        .param("email", "ana@example.com")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookupWithServiceTokenIsAllowed() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.lookup(eq("ana@example.com"), any())).thenReturn(new UserLookupResponse(userId, "Ana Souza", "ana@example.com", "52998224725"));
        String serviceToken = jwtService.generateServiceToken("account-service", List.of("users:lookup"));

        mockMvc.perform(get("/auth/users/lookup")
                        .param("email", "ana@example.com")
                        .header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isOk());
    }

    @Test
    void serviceTokenEndpointIsPublic() throws Exception {
        when(serviceTokenService.issue(any())).thenReturn(new ServiceTokenResponse("token", "Bearer", 300));

        mockMvc.perform(post("/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"account-service","clientSecret":"s3cret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"));
    }

    @Test
    void serviceTokenEndpointRejectsBadCredentials() throws Exception {
        when(serviceTokenService.issue(any())).thenThrow(new InvalidClientCredentialsException());

        mockMvc.perform(post("/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"account-service","clientSecret":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String userToken() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ana@example.com");
        user.setFullName("Ana Souza");
        return jwtService.generateToken(user);
    }
}
