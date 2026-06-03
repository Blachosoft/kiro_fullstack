package com.supermarket.sales.dto.request;

import com.supermarket.sales.domain.PaymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CheckoutRequest(
        @NotNull PaymentType paymentType,
        @PositiveOrZero BigDecimal amountReceived,
        Long customerId
) {
}
