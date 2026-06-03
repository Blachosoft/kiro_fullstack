package com.supermarket.sales.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReturnItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        @NotBlank @Size(max = 255) String reason
) {
}
