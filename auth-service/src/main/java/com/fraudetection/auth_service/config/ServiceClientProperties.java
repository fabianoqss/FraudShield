package com.fraudetection.auth_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "service-clients")
public record ServiceClientProperties(Map<String, Client> clients) {

    public ServiceClientProperties {
        clients = clients == null ? Map.of() : clients;
    }

    public record Client(String secret, List<String> scopes) {

        public Client {
            scopes = scopes == null ? List.of() : scopes;
        }
    }
}
