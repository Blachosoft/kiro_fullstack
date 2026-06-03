package com.supermarket.sales.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.supermarket.sales.domain.CreditStatus;
import com.supermarket.sales.exception.ExternalServiceException;
import com.supermarket.sales.exception.ExternalServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class CustomerApiClientTest {

    private MockRestServiceServer server;
    private CustomerApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().rootUri("http://customer-api").build();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new CustomerApiClient(restTemplate);
    }

    @Test
    void searchesAndLooksUpCustomers() {
        String customerJson = "{\"id\":10,\"fullName\":\"Ana Customer\",\"documentType\":\"CC\",\"documentNumber\":\"123\",\"creditStatus\":\"APPROVED\"}";
        server.expect(once(), requestTo("http://customer-api/api/customers/search?name=ana"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + customerJson + "]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://customer-api/api/customers/search?documentNumber=123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(customerJson, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://customer-api/api/customers/10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(customerJson, MediaType.APPLICATION_JSON));

        assertThat(client.searchByName("ana")).hasSize(1);
        assertThat(client.searchByDocumentNumber("123").documentType()).isEqualTo("CC");
        assertThat(client.getCustomer(10L).creditStatus()).isEqualTo(CreditStatus.APPROVED);
        server.verify();
    }

    @Test
    void mapsUpstreamAndConnectionFailures() {
        server.expect(once(), requestTo("http://customer-api/api/customers/99"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> client.getCustomer(99L))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Customer service error");

        RestTemplate unavailableRestTemplate = new RestTemplateBuilder()
                .rootUri("http://customer-api")
                .additionalInterceptors((request, body, execution) -> {
                    throw new ResourceAccessException("timeout");
                })
                .build();
        CustomerApiClient unavailableClient = new CustomerApiClient(unavailableRestTemplate);

        assertThatThrownBy(() -> unavailableClient.searchByDocumentNumber("123"))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("Customer service unavailable");
    }
}
