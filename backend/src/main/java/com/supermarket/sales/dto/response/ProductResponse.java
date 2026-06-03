package com.supermarket.sales.dto.response;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String barcode,
        BigDecimal unitPrice,
        Integer availableStock,
        String category
) {
}
