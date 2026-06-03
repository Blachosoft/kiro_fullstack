package com.supermarket.sales.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PartialReturnRequest(@NotEmpty List<@Valid ReturnItemRequest> items) {
}
