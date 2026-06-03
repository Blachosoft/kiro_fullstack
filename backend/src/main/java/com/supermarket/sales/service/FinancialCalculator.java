package com.supermarket.sales.service;

import com.supermarket.sales.domain.SaleItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinancialCalculator {

    private static final int MONEY_SCALE = 2;

    public BigDecimal calculateLineTotal(BigDecimal unitPrice, int quantity) {
        return money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public BigDecimal calculateSubtotal(List<SaleItem> items) {
        BigDecimal subtotal = items.stream()
                .map(SaleItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return money(subtotal);
    }

    public BigDecimal calculateTax(BigDecimal subtotal, BigDecimal taxRate) {
        return money(subtotal.multiply(taxRate));
    }

    public BigDecimal calculateTotal(BigDecimal subtotal, BigDecimal tax, BigDecimal discount) {
        return money(subtotal.add(tax).subtract(discount));
    }

    public BigDecimal calculateChange(BigDecimal amountReceived, BigDecimal total) {
        return money(amountReceived.subtract(total));
    }

    public BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
