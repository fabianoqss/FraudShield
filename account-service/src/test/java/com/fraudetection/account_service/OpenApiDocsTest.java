package com.fraudetection.account_service;

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
                .andExpect(jsonPath("$.paths['/accounts']").exists())
                .andExpect(jsonPath("$.paths['/accounts/pix-keys/lookup']").exists());
    }

    @Test
    void internalRoutesStayOutOfTheSpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/internal/pix-keys/lookups/{lookupId}']").doesNotExist());
    }

    @Test
    void specDeclaresBearerAuthAndIsServedThroughTheGateway() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void pixKeyFieldsDescribeTheAcceptedFormats() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.PixKeyLookupRequest.properties.key.description")
                        .value(containsString("random key (UUID)")))
                .andExpect(jsonPath("$.components.schemas.PixKeyLookupRequest.properties.key.example").exists())
                .andExpect(jsonPath("$.components.schemas.PixDepositRequest.properties.pixKey.description")
                        .value(containsString("random key (UUID)")))
                .andExpect(jsonPath("$.components.schemas.PixDepositRequest.properties.pixKey.example").exists());
    }

    @Test
    void lookupDocumentsItsErrorResponses() throws Exception {
        String lookup = "$.paths['/accounts/pix-keys/lookup'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(lookup + ".tags[0]").value("PIX keys"))
                .andExpect(jsonPath(lookup + ".summary").exists())
                .andExpect(jsonPath(lookup + ".responses['400'].description").value(containsString("CPF")))
                .andExpect(jsonPath(lookup + ".responses['400'].content['application/json'].schema['$ref']")
                        .value(endsWith("/ErrorResponse")))
                .andExpect(jsonPath(lookup + ".responses['404']").exists())
                .andExpect(jsonPath(lookup + ".responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath(lookup + ".responses['503']").exists());
    }

    @Test
    void depositDocumentsItsErrorResponses() throws Exception {
        String deposit = "$.paths['/accounts/deposit'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(deposit + ".tags[0]").value("Accounts"))
                .andExpect(jsonPath(deposit + ".responses['400'].description").value(containsString("CPF")))
                .andExpect(jsonPath(deposit + ".responses['404']").exists());
    }

    @Test
    void publicDepositNeedsNoToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/accounts/deposit'].post.security").isEmpty());
    }
}
