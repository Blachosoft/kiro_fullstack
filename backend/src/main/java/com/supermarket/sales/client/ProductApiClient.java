package com.supermarket.sales.client;

import com.supermarket.sales.dto.response.ProductResponse;
import com.supermarket.sales.exception.ExternalServiceException;
import com.supermarket.sales.exception.ExternalServiceUnavailableException;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
@Profile("!dev")
public class ProductApiClient implements ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductApiClient.class);
    private final RestTemplate restTemplate;

    public ProductApiClient(@Qualifier("productRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<ProductResponse> findAll() {
        ProductResponse[] response = execute(
                () -> restTemplate.getForObject("/api/products", ProductResponse[].class),
                "list products"
        );
        return response == null ? List.of() : Arrays.asList(response);
    }

    @Override
    public List<ProductResponse> searchByName(String name) {
        ProductResponse[] response = execute(
                () -> restTemplate.getForObject("/api/products/search?name={name}", ProductResponse[].class, name),
                "search products by name"
        );
        return response == null ? List.of() : Arrays.asList(response);
    }

    @Override
    public ProductResponse searchByBarcode(String barcode) {
        return execute(
                () -> restTemplate.getForObject("/api/products/search?barcode={barcode}", ProductResponse.class, barcode),
                "search product by barcode"
        );
    }

    @Override
    public ProductResponse getProduct(Long productId) {
        return execute(
                () -> restTemplate.getForObject("/api/products/{productId}", ProductResponse.class, productId),
                "get product"
        );
    }

    @Override
    public void decrementStock(Long productId, int quantity) {
        executeVoid(
                () -> restTemplate.postForLocation("/api/products/{productId}/stock/decrement?quantity={quantity}", null, productId, quantity),
                "decrement product stock"
        );
    }

    @Override
    public void incrementStock(Long productId, int quantity) {
        executeVoid(
                () -> restTemplate.postForLocation("/api/products/{productId}/stock/increment?quantity={quantity}", null, productId, quantity),
                "increment product stock"
        );
    }

    private <T> T execute(ClientCall<T> call, String action) {
        try {
            log.debug("Calling Product API to {}", action);
            T response = call.execute();
            log.debug("Product API {} completed", action);
            return response;
        } catch (ResourceAccessException exception) {
            throw new ExternalServiceUnavailableException("Product service unavailable");
        } catch (RestClientResponseException exception) {
            throw new ExternalServiceException("Product service error");
        }
    }

    private void executeVoid(VoidClientCall call, String action) {
        execute(() -> {
            call.execute();
            return null;
        }, action);
    }

    @FunctionalInterface
    private interface ClientCall<T> {
        T execute();
    }

    @FunctionalInterface
    private interface VoidClientCall {
        void execute();
    }
}
