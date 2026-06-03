package com.supermarket.sales.dto.response;

import com.supermarket.sales.domain.PaymentType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptResponse(
        String receiptId,
        String storeName,
        String terminalId,
        String cashierId,
        LocalDateTime timestamp,
        Long customerId,
        List<ReceiptItemResponse> items,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal total,
        PaymentType paymentMethod,
        BigDecimal amountReceived,
        BigDecimal change,
        String transactionId,
        String creditReferenceNumber,
        String originalTransactionId,
        String refundType
) {
}
