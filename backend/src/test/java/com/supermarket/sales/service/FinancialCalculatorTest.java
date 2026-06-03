package com.supermarket.sales.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarket.sales.domain.SaleItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialCalculatorTest {

    private final FinancialCalculator calculator = new FinancialCalculator();

    @Test
    void calculatesLineTotalWithHalfUpMoneyScale() {
        BigDecimal lineTotal = calculator.calculateLineTotal(new BigDecimal("10.235"), 2);

        assertThat(lineTotal).isEqualByComparingTo("20.47");
    }

    @Test
    void calculatesSubtotalTaxTotalAndChange() {
        SaleItem first = item("10.00");
        SaleItem second = item("5.50");

        BigDecimal subtotal = calculator.calculateSubtotal(List.of(first, second));
        BigDecimal tax = calculator.calculateTax(subtotal, new BigDecimal("0.19"));
        BigDecimal total = calculator.calculateTotal(subtotal, tax, BigDecimal.ONE);
        BigDecimal change = calculator.calculateChange(new BigDecimal("20.00"), total);

        assertThat(subtotal).isEqualByComparingTo("15.50");
        assertThat(tax).isEqualByComparingTo("2.95");
        assertThat(total).isEqualByComparingTo("17.45");
        assertThat(change).isEqualByComparingTo("2.55");
    }

    private SaleItem item(String lineTotal) {
        SaleItem item = new SaleItem();
        item.setLineTotal(new BigDecimal(lineTotal));
        return item;
    }
}
