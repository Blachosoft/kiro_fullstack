package com.supermarket.sales.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSaleRequest(
        @NotBlank String terminalId,
        @NotBlank String cashierId,
        Long customerId
) {
}
