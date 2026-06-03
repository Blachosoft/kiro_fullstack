package com.supermarket.sales.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(SalesProperties.class)
public class HttpClientConfig {

    @Bean
    @Qualifier("productRestTemplate")
    RestTemplate productRestTemplate(RestTemplateBuilder builder, SalesProperties properties) {
        SalesProperties.ExternalApi api = properties.external().productApi();
        return builder
                .rootUri(api.baseUrl())
                .setConnectTimeout(api.connectTimeout())
                .setReadTimeout(api.readTimeout())
                .build();
    }

    @Bean
    @Qualifier("customerRestTemplate")
    RestTemplate customerRestTemplate(RestTemplateBuilder builder, SalesProperties properties) {
        SalesProperties.ExternalApi api = properties.external().customerApi();
        return builder
                .rootUri(api.baseUrl())
                .setConnectTimeout(api.connectTimeout())
                .setReadTimeout(api.readTimeout())
                .build();
    }
}
