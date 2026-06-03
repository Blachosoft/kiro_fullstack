package com.supermarket.sales.client;

import com.supermarket.sales.dto.response.ProductResponse;
import java.util.List;

public interface ProductClient {

    List<ProductResponse> findAll();

    List<ProductResponse> searchByName(String name);

    ProductResponse searchByBarcode(String barcode);

    ProductResponse getProduct(Long productId);

    void decrementStock(Long productId, int quantity);

    void incrementStock(Long productId, int quantity);
}
