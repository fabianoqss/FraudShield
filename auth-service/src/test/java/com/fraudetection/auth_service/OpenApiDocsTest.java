package com.fraudetection.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OpenApiDocsTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void specIsPublicAndDocumentsTheUserRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/auth/password']").exists());
    }

    @Test
    void internalRoutesStayOutOfTheSpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/auth/service-token']").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/users/lookup']").doesNotExist())
                .andExpect(jsonPath("$.paths['/.well-known/jwks.json']").doesNotExist());
    }

    @Test
    void specDeclaresBearerAuthAndIsServedThroughTheGateway() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void publicRoutesNeedNoToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/auth/logout'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/auth/password'].put.security").doesNotExist());
    }

    @Test
    void loginDocumentsItsErrorResponses() throws Exception {
        String login = "$.paths['/auth/login'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(login + ".tags[0]").value("Auth"))
                .andExpect(jsonPath(login + ".summary").exists())
                .andExpect(jsonPath(login + ".responses['401'].content['application/json'].schema['$ref']")
                        .value(endsWith("/ErrorResponse")))
                .andExpect(jsonPath(login + ".responses['429']").exists());
    }

    @Test
    void refreshWarnsThatTheOldTokenStopsWorking() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/auth/refresh'].post.description").value(containsString("revokes")))
                .andExpect(jsonPath("$.paths['/auth/register'].post.responses['409']").exists());
    }

    @Test
    void registerFieldsDescribeTheirRules() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.cpf.description")
                        .value(containsString("11 digits")))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password.description")
                        .value(containsString("special character")))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.birthDate.description")
                        .value(containsString("18")));
    }
}
