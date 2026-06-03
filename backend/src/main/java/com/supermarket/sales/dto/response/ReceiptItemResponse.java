package com.supermarket.sales.dto.response;

import java.math.BigDecimal;

public record ReceiptItemResponse(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {
}
