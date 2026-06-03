package com.supermarket.products.repository;

import com.supermarket.products.domain.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByBarcode(String barcode);

    List<Product> findAll();

    List<Product> searchByName(String name);
}
