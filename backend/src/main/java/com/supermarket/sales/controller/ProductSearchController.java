package com.supermarket.sales.controller;

import com.supermarket.sales.client.ProductClient;
import com.supermarket.sales.dto.response.ProductResponse;
import com.supermarket.sales.exception.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductSearchController {

    private final ProductClient productClient;

    public ProductSearchController(ProductClient productClient) {
        this.productClient = productClient;
    }

    @Operation(summary = "List products through the configured Product API")
    @GetMapping
    public List<ProductResponse> findAll() {
        return productClient.findAll();
    }

    @Operation(summary = "Search products through the external Product API")
    @GetMapping("/search")
    public Object search(@RequestParam(required = false) String name, @RequestParam(required = false) String barcode) {
        if (barcode != null && !barcode.isBlank()) {
            return productClient.searchByBarcode(barcode);
        }
        if (name != null && !name.isBlank()) {
            return productClient.searchByName(name);
        }
        throw new BusinessRuleException("Either name or barcode is required");
    }
}
