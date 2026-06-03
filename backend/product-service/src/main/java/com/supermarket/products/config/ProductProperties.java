package com.supermarket.products.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "products")
public record ProductProperties(String tableName) {
}
