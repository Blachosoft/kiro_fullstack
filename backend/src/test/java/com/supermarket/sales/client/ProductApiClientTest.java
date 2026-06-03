package com.supermarket.sales.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadGateway;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.supermarket.sales.exception.ExternalServiceException;
import com.supermarket.sales.exception.ExternalServiceUnavailableException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class ProductApiClientTest {

    private MockRestServiceServer server;
    private ProductApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().rootUri("http://product-api").build();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new ProductApiClient(restTemplate);
    }

    @Test
    void searchesProductsByNameAndBarcode() {
        server.expect(once(), requestTo("http://product-api/api/products/search?name=milk"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":1,\"name\":\"Milk\",\"barcode\":\"111\",\"unitPrice\":10.00,\"availableStock\":20,\"category\":\"Dairy\"}]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://product-api/api/products/search?barcode=111"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"Milk\",\"barcode\":\"111\",\"unitPrice\":10.00,\"availableStock\":20,\"category\":\"Dairy\"}", MediaType.APPLICATION_JSON));

        assertThat(client.searchByName("milk")).hasSize(1);
        assertThat(client.searchByBarcode("111").unitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        server.verify();
    }

    @Test
    void looksUpProductsAndUpdatesStock() {
        server.expect(once(), requestTo("http://product-api/api/products/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"Milk\",\"barcode\":\"111\",\"unitPrice\":10.00,\"availableStock\":20,\"category\":\"Dairy\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://product-api/api/products/1/stock/decrement?quantity=2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        server.expect(once(), requestTo("http://product-api/api/products/1/stock/increment?quantity=1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertThat(client.getProduct(1L).name()).isEqualTo("Milk");
        client.decrementStock(1L, 2);
        client.incrementStock(1L, 1);
        server.verify();
    }

    @Test
    void mapsUpstreamAndConnectionFailures() {
        server.expect(once(), requestTo("http://product-api/api/products/9"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> client.getProduct(9L))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Product service error");

        RestTemplate unavailableRestTemplate = new RestTemplateBuilder()
                .rootUri("http://product-api")
                .additionalInterceptors((request, body, execution) -> {
                    throw new ResourceAccessException("timeout");
                })
                .build();
        ProductApiClient unavailableClient = new ProductApiClient(unavailableRestTemplate);

        assertThatThrownBy(() -> unavailableClient.searchByName("milk"))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Product service unavailable");
    }
}
