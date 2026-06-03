package com.supermarket.sales.dto.response;

import java.math.BigDecimal;

public record CheckoutResponse(
        String transactionId,
        ReceiptResponse receipt,
        BigDecimal change,
        String creditReferenceNumber
) {
}
