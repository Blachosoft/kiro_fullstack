package com.supermarket.sales.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI salesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sales API")
                        .version("v1")
                        .description("REST API for supermarket point-of-sale sales transactions"));
    }
}
