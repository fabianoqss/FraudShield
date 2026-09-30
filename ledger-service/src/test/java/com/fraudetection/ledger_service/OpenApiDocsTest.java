package com.fraudetection.ledger_service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OpenApiDocsTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void specIsPublicAndDocumentsTheUserRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/ledger/account/{id}']").exists());
    }

    @Test
    void specDeclaresBearerAuthAndIsServedThroughTheGateway() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void accountLedgerDocumentsItsErrorResponses() throws Exception {
        String ledger = "$.paths['/ledger/account/{id}'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(ledger + ".tags[0]").value("Ledger"))
                .andExpect(jsonPath(ledger + ".summary").exists())
                .andExpect(jsonPath(ledger + ".responses['403'].content['application/json'].schema['$ref']")
                        .value(endsWith("/ErrorResponse")))
                .andExpect(jsonPath(ledger + ".responses['400']").exists())
                .andExpect(jsonPath(ledger + ".responses['404']").exists())
                .andExpect(jsonPath(ledger + ".responses['503']").exists());
    }
}
