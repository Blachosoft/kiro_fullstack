package com.supermarket.products.service;

import com.supermarket.products.domain.Product;
import com.supermarket.products.dto.ProductRequest;
import com.supermarket.products.dto.ProductResponse;
import com.supermarket.products.exception.BusinessRuleException;
import com.supermarket.products.exception.ResourceNotFoundException;
import com.supermarket.products.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse createOrUpdate(ProductRequest request) {
        Product product = new Product();
        product.setId(request.id());
        product.setName(request.name());
        product.setBarcode(request.barcode());
        product.setUnitPrice(request.unitPrice());
        product.setAvailableStock(request.availableStock());
        product.setCategory(request.category());
        return toResponse(productRepository.save(product));
    }

    public ProductResponse getById(Long productId) {
        return productRepository.findById(productId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    public List<ProductResponse> findAll() {
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProductResponse> searchByName(String name) {
        return productRepository.searchByName(name)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductResponse searchByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    public ProductResponse decrementStock(Long productId, int quantity) {
        Product product = findProduct(productId);
        if (quantity < 1) {
            throw new BusinessRuleException("Quantity must be at least 1");
        }
        if (product.getAvailableStock() == null || product.getAvailableStock() < quantity) {
            throw new BusinessRuleException("Insufficient stock for product");
        }
        product.setAvailableStock(product.getAvailableStock() - quantity);
        return toResponse(productRepository.save(product));
    }

    public ProductResponse incrementStock(Long productId, int quantity) {
        Product product = findProduct(productId);
        if (quantity < 1) {
            throw new BusinessRuleException("Quantity must be at least 1");
        }
        product.setAvailableStock(product.getAvailableStock() + quantity);
        return toResponse(productRepository.save(product));
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getBarcode(),
                product.getUnitPrice(),
                product.getAvailableStock(),
                product.getCategory()
        );
    }
}
