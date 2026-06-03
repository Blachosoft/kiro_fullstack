package com.supermarket.sales.controller;

import com.supermarket.sales.client.CustomerClient;
import com.supermarket.sales.exception.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerSearchController {

    private final CustomerClient customerClient;

    public CustomerSearchController(CustomerClient customerClient) {
        this.customerClient = customerClient;
    }

    @Operation(summary = "Search customers through the external Customer API")
    @GetMapping("/search")
    public Object search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String documentNumber
    ) {
        if (documentNumber != null && !documentNumber.isBlank()) {
            return customerClient.searchByDocumentNumber(documentNumber);
        }
        if (name != null && !name.isBlank()) {
            return customerClient.searchByName(name);
        }
        throw new BusinessRuleException("Either name or documentNumber is required");
    }
}
