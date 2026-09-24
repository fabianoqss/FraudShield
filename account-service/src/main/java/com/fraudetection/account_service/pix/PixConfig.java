package com.fraudetection.account_service.pix;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PixProperties.class)
class PixConfig {
}
