package com.supermarket.sales.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.sales.dto.response.ProductResponse;
import com.supermarket.sales.exception.ResourceNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class LocalJsonProductClient implements ProductClient {

    private final ObjectMapper objectMapper;
    private final Path productDataFile;

    public LocalJsonProductClient(
            ObjectMapper objectMapper,
            @Value("${sales.local-products-file:../frontend/data/productos.json}") String productDataFile
    ) {
        this.objectMapper = objectMapper;
        this.productDataFile = Path.of(productDataFile);
    }

    @Override
    public List<ProductResponse> findAll() {
        return readProducts().stream()
                .filter(LocalProduct::active)
                .map(LocalProduct::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> searchByName(String name) {
        String query = normalize(name);
        return findAll().stream()
                .filter(product -> normalize(product.name()).contains(query)
                        || normalize(product.category()).contains(query))
                .toList();
    }

    @Override
    public ProductResponse searchByBarcode(String barcode) {
        return readProducts().stream()
                .filter(LocalProduct::active)
                .filter(product -> barcode.equals(String.valueOf(product.id())))
                .findFirst()
                .map(LocalProduct::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Override
    public ProductResponse getProduct(Long productId) {
        return readProducts().stream()
                .filter(LocalProduct::active)
                .filter(product -> product.id().equals(productId))
                .findFirst()
                .map(LocalProduct::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Override
    public void decrementStock(Long productId, int quantity) {
        getProduct(productId);
    }

    @Override
    public void incrementStock(Long productId, int quantity) {
        getProduct(productId);
    }

    private List<LocalProduct> readProducts() {
        try {
            byte[] json = Files.readAllBytes(productDataFile);
            return objectMapper.readerForListOf(LocalProduct.class).readValue(json);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read local products from " + productDataFile, exception);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LocalProduct(
            Long id,
            String nombre,
            String subcategoria,
            BigDecimal precio,
            String estado
    ) {
        boolean active() {
            return "activo".equalsIgnoreCase(estado);
        }

        ProductResponse toResponse() {
            return new ProductResponse(id, nombre, String.valueOf(id), precio, 999, subcategoria);
        }
    }
}
