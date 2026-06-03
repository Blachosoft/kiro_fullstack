package com.supermarket.sales.client;

import com.supermarket.sales.dto.response.CustomerResponse;
import com.supermarket.sales.exception.ExternalServiceException;
import com.supermarket.sales.exception.ExternalServiceUnavailableException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
public class CustomerApiClient implements CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerApiClient.class);
    private final RestTemplate restTemplate;

    public CustomerApiClient(@Qualifier("customerRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<CustomerResponse> searchByName(String name) {
        CustomerResponse[] response = execute(
                () -> restTemplate.getForObject("/api/customers/search?name={name}", CustomerResponse[].class, name),
                "search customers by name"
        );
        return response == null ? List.of() : Arrays.asList(response);
    }

    @Override
    public CustomerResponse searchByDocumentNumber(String documentNumber) {
        return execute(
                () -> restTemplate.getForObject("/api/customers/search?documentNumber={documentNumber}", CustomerResponse.class, documentNumber),
                "search customer by document number"
        );
    }

    @Override
    public CustomerResponse getCustomer(Long customerId) {
        return execute(
                () -> restTemplate.getForObject("/api/customers/{customerId}", CustomerResponse.class, customerId),
                "get customer"
        );
    }

    private <T> T execute(ClientCall<T> call, String action) {
        try {
            log.debug("Calling Customer API to {}", action);
            T response = call.execute();
            log.debug("Customer API {} completed", action);
            return response;
        } catch (ResourceAccessException exception) {
            throw new ExternalServiceUnavailableException("Customer service unavailable");
        } catch (RestClientResponseException exception) {
            throw new ExternalServiceException("Customer service error");
        }
    }

    @FunctionalInterface
    private interface ClientCall<T> {
        T execute();
    }
}
