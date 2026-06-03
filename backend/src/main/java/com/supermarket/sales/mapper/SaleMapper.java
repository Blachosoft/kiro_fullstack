package com.supermarket.sales.mapper;

import com.supermarket.sales.domain.Sale;
import com.supermarket.sales.domain.SaleItem;
import com.supermarket.sales.dto.response.SaleItemResponse;
import com.supermarket.sales.dto.response.SaleResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SaleMapper {

    public SaleResponse toResponse(Sale sale) {
        List<SaleItemResponse> items = sale.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new SaleResponse(
                sale.getId(),
                sale.getTerminalId(),
                sale.getCashierId(),
                sale.getCustomerId(),
                sale.getStatus(),
                items,
                sale.getSubtotal(),
                sale.getTax(),
                sale.getDiscount(),
                sale.getTotal(),
                sale.getTransactionId(),
                sale.getCreatedAt(),
                sale.getFrozenAt(),
                sale.getResumedAt(),
                sale.getCompletedAt(),
                sale.getCancelledAt(),
                sale.getCancellationReason()
        );
    }

    private SaleItemResponse toItemResponse(SaleItem item) {
        return new SaleItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }
}
