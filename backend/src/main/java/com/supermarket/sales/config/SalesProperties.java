package com.supermarket.sales.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sales")
public record SalesProperties(
        String storeName,
        BigDecimal taxRate,
        Duration freezeExpiration,
        External external
) {
    public record External(ExternalApi productApi, ExternalApi customerApi) {
    }

    public record ExternalApi(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }
}
