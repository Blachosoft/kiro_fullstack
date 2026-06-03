package com.supermarket.products.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supermarket.products.domain.Product;
import com.supermarket.products.dto.ProductRequest;
import com.supermarket.products.exception.BusinessRuleException;
import com.supermarket.products.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductServiceTest {

    private ProductService productService;
    private InMemoryProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        productService = new ProductService(productRepository);
    }

    @Test
    void createsSearchesAndFindsProducts() {
        productService.createOrUpdate(new ProductRequest(1L, "Whole Milk", "111", new BigDecimal("10.00"), 20, "Dairy"));

        assertThat(productService.getById(1L).name()).isEqualTo("Whole Milk");
        assertThat(productService.findAll()).hasSize(1);
        assertThat(productService.searchByName("milk")).hasSize(1);
        assertThat(productService.searchByBarcode("111").availableStock()).isEqualTo(20);
    }

    @Test
    void decrementAndIncrementStock() {
        productService.createOrUpdate(new ProductRequest(1L, "Rice", "222", new BigDecimal("5.00"), 10, "Pantry"));

        assertThat(productService.decrementStock(1L, 4).availableStock()).isEqualTo(6);
        assertThat(productService.incrementStock(1L, 3).availableStock()).isEqualTo(9);
    }

    @Test
    void rejectsStockDecrementWhenStockIsInsufficient() {
        productService.createOrUpdate(new ProductRequest(1L, "Rice", "222", new BigDecimal("5.00"), 2, "Pantry"));

        assertThatThrownBy(() -> productService.decrementStock(1L, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Insufficient stock for product");
    }

    private static class InMemoryProductRepository implements ProductRepository {

        private final Map<Long, Product> products = new HashMap<>();

        @Override
        public Product save(Product product) {
            products.put(product.getId(), product);
            return product;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public Optional<Product> findByBarcode(String barcode) {
            return products.values()
                    .stream()
                    .filter(product -> barcode.equals(product.getBarcode()))
                    .findFirst();
        }

        @Override
        public List<Product> findAll() {
            return products.values()
                    .stream()
                    .toList();
        }

        @Override
        public List<Product> searchByName(String name) {
            String normalized = name.toLowerCase();
            return products.values()
                    .stream()
                    .filter(product -> product.getNormalizedName().contains(normalized))
                    .toList();
        }
    }
}
