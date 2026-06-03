package com.supermarket.sales.dto.response;

import com.supermarket.sales.domain.CreditStatus;

public record CustomerResponse(
        Long id,
        String fullName,
        String documentType,
        String documentNumber,
        CreditStatus creditStatus
) {
}
