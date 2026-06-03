package com.supermarket.sales.dto.response;

import com.supermarket.sales.domain.SaleState;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleResponse(
        Long id,
        String terminalId,
        String cashierId,
        Long customerId,
        SaleState status,
        List<SaleItemResponse> items,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal total,
        String transactionId,
        LocalDateTime createdAt,
        LocalDateTime frozenAt,
        LocalDateTime resumedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        String cancellationReason
) {
}
