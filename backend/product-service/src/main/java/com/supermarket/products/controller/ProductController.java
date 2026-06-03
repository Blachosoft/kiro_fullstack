package com.supermarket.products.controller;

import com.supermarket.products.dto.ProductRequest;
import com.supermarket.products.dto.ProductResponse;
import com.supermarket.products.exception.BusinessRuleException;
import com.supermarket.products.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createOrUpdate(@Valid @RequestBody ProductRequest request) {
        return productService.createOrUpdate(request);
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll();
    }

    @GetMapping("/{productId}")
    public ProductResponse getById(@PathVariable Long productId) {
        return productService.getById(productId);
    }

    @GetMapping("/search")
    public Object search(@RequestParam(required = false) String name, @RequestParam(required = false) String barcode) {
        if (barcode != null && !barcode.isBlank()) {
            return productService.searchByBarcode(barcode);
        }
        if (name != null && !name.isBlank()) {
            return productService.searchByName(name);
        }
        throw new BusinessRuleException("Either name or barcode is required");
    }

    @PostMapping("/{productId}/stock/decrement")
    public ProductResponse decrementStock(@PathVariable Long productId, @RequestParam @Min(1) int quantity) {
        return productService.decrementStock(productId, quantity);
    }

    @PostMapping("/{productId}/stock/increment")
    public ProductResponse incrementStock(@PathVariable Long productId, @RequestParam @Min(1) int quantity) {
        return productService.incrementStock(productId, quantity);
    }
}
