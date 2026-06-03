package com.supermarket.products.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ProductRequest(
        @NotNull Long id,
        @NotBlank String name,
        @NotBlank String barcode,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        @NotNull @Min(0) Integer availableStock,
        @NotBlank String category
) {
}
