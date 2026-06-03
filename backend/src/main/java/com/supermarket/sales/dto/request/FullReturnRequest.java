package com.supermarket.sales.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FullReturnRequest(@NotBlank @Size(max = 255) String reason) {
}
