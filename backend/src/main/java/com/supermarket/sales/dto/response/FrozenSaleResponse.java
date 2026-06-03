package com.supermarket.sales.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FrozenSaleResponse(
        Long saleId,
        String customerName,
        Integer itemCount,
        BigDecimal totalAmount,
        LocalDateTime freezeTimestamp
) {
}
