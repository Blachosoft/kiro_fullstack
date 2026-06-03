package com.supermarket.products.repository;

import com.supermarket.products.config.ProductProperties;
import com.supermarket.products.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class DynamoProductRepository implements ProductRepository {

    private final DynamoDbTable<Product> table;

    public DynamoProductRepository(DynamoDbEnhancedClient enhancedClient, ProductProperties properties) {
        this.table = enhancedClient.table(properties.tableName(), TableSchema.fromBean(Product.class));
    }

    @Override
    public Product save(Product product) {
        table.putItem(product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        Product product = table.getItem(Key.builder().partitionValue(id).build());
        return Optional.ofNullable(product);
    }

    @Override
    public Optional<Product> findByBarcode(String barcode) {
        return table.scan()
                .items()
                .stream()
                .filter(product -> barcode.equals(product.getBarcode()))
                .findFirst();
    }

    @Override
    public List<Product> findAll() {
        return table.scan()
                .items()
                .stream()
                .toList();
    }

    @Override
    public List<Product> searchByName(String name) {
        String normalized = name.toLowerCase();
        return table.scan()
                .items()
                .stream()
                .filter(product -> product.getNormalizedName() != null && product.getNormalizedName().contains(normalized))
                .toList();
    }
}
