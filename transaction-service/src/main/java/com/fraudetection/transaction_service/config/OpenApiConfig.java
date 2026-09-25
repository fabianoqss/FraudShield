package com.fraudetection.transaction_service.config;

import com.fraudetection.transaction_service.dto.response.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FraudShield Transaction API")
                        .description("PIX transfers and transaction history.")
                        .version("v1"))
                // Relative, so "Try it out" goes to whichever host served the spec: the gateway.
                .addServersItem(new Server().url("/"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    // Every error this service returns has the ErrorResponse body, so the @ApiResponse declarations only
    // carry the status and its meaning.
    @Bean
    public OpenApiCustomizer errorResponseBody() {
        return openApi -> {
            ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(openApi.getComponents()::addSchemas);
            Content errorBody = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")));
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((status, response) -> {
                        if (status.startsWith("4") || status.startsWith("5")) {
                            response.setContent(errorBody);
                        }
                    })));
        };
    }
}
