package com.supermarket.sales.client;

import com.supermarket.sales.dto.response.CustomerResponse;
import java.util.List;

public interface CustomerClient {

    List<CustomerResponse> searchByName(String name);

    CustomerResponse searchByDocumentNumber(String documentNumber);

    CustomerResponse getCustomer(Long customerId);
}
