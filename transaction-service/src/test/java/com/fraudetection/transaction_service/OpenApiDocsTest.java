package com.fraudetection.transaction_service;

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
                .andExpect(jsonPath("$.paths['/transactions/{id}']").exists());
    }

    @Test
    void specDeclaresBearerAuthAndIsServedThroughTheGateway() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void createDocumentsItsErrorResponsesAndTheStatusLifecycle() throws Exception {
        String create = "$.paths['/transactions'].post";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(create + ".tags[0]").value("Transactions"))
                .andExpect(jsonPath(create + ".description").value(containsString("FLAGGED")))
                .andExpect(jsonPath(create + ".responses['409']").exists())
                .andExpect(jsonPath(create + ".responses['422'].description").value(containsString("lookup")))
                .andExpect(jsonPath(create + ".responses['422'].content['application/json'].schema['$ref']")
                        .value(endsWith("/ErrorResponse")))
                .andExpect(jsonPath(create + ".responses['503']").exists());
    }

    @Test
    void getAndListDocumentTheirErrorResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/transactions/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/transactions'].get.responses['403']").exists());
    }

    @Test
    void requestFieldsExplainHowAClientFillsThem() throws Exception {
        String fields = "$.components.schemas.TransactionRequest.properties";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(fields + ".lookupId.description").value(containsString("pix-keys/lookup")))
                .andExpect(jsonPath(fields + ".idempotencyKey.description").value(containsString("retry")))
                .andExpect(jsonPath(fields + ".deviceId.description").exists())
                .andExpect(jsonPath(fields + ".ipAddress").doesNotExist());
    }
}
