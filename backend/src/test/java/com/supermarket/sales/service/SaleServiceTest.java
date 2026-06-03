package com.supermarket.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supermarket.sales.client.CustomerClient;
import com.supermarket.sales.client.ProductClient;
import com.supermarket.sales.config.SalesProperties;
import com.supermarket.sales.domain.CreditStatus;
import com.supermarket.sales.domain.Payment;
import com.supermarket.sales.domain.PaymentType;
import com.supermarket.sales.domain.Sale;
import com.supermarket.sales.domain.SaleItem;
import com.supermarket.sales.domain.SaleState;
import com.supermarket.sales.dto.request.AddItemRequest;
import com.supermarket.sales.dto.request.CheckoutRequest;
import com.supermarket.sales.dto.request.CreateSaleRequest;
import com.supermarket.sales.dto.request.PartialReturnRequest;
import com.supermarket.sales.dto.request.ReturnItemRequest;
import com.supermarket.sales.dto.response.CustomerResponse;
import com.supermarket.sales.dto.response.ProductResponse;
import com.supermarket.sales.exception.BusinessRuleException;
import com.supermarket.sales.mapper.SaleMapper;
import com.supermarket.sales.repository.PaymentRepository;
import com.supermarket.sales.repository.ReturnItemRepository;
import com.supermarket.sales.repository.ReturnRepository;
import com.supermarket.sales.repository.SaleItemRepository;
import com.supermarket.sales.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SaleItemRepository saleItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReturnRepository returnRepository;

    @Mock
    private ReturnItemRepository returnItemRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private CustomerClient customerClient;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        SalesProperties properties = new SalesProperties("Test Store", new BigDecimal("0.19"), Duration.ofHours(2), null);
        saleService = new SaleService(
                saleRepository,
                saleItemRepository,
                paymentRepository,
                returnRepository,
                returnItemRepository,
                productClient,
                customerClient,
                new FinancialCalculator(),
                properties,
                new SaleMapper()
        );
    }

    @Test
    void createSaleInitializesActiveSaleWithZeroTotals() {
        when(saleRepository.save(any(Sale.class))).thenAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            setId(sale, 1L);
            return sale;
        });

        var response = saleService.createSale(new CreateSaleRequest("POS-1", "cashier-1", null));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(SaleState.ACTIVE);
        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualByComparingTo("0.00");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void addItemMergesExistingProductAndRecalculatesTotals() {
        Sale sale = activeSale(1L);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(productClient.getProduct(10L)).thenReturn(product(10L, "Milk", "10.00", 10));
        when(saleRepository.save(sale)).thenReturn(sale);

        saleService.addItem(1L, new AddItemRequest(10L, 2));
        var response = saleService.addItem(1L, new AddItemRequest(10L, 3));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(5);
        assertThat(response.subtotal()).isEqualByComparingTo("50.00");
        assertThat(response.tax()).isEqualByComparingTo("9.50");
        assertThat(response.total()).isEqualByComparingTo("59.50");
    }

    @Test
    void cashCheckoutCompletesSalePersistsPaymentAndDecrementsStock() {
        Sale sale = activeSale(1L);
        sale.addItem(item(10L, "Milk", "10.00", 2));
        saleService.recalculateTotals(sale);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(productClient.getProduct(10L)).thenReturn(product(10L, "Milk", "10.00", 5));
        when(saleRepository.save(sale)).thenReturn(sale);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = saleService.checkout(1L, new CheckoutRequest(PaymentType.CASH, new BigDecimal("30.00"), null));

        assertThat(sale.getStatus()).isEqualTo(SaleState.COMPLETED);
        assertThat(response.transactionId()).startsWith("TX-");
        assertThat(response.change()).isEqualByComparingTo("6.20");
        verify(productClient).decrementStock(10L, 2);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void creditCheckoutRejectsCustomerWithoutApprovedCreditAndDoesNotDecrementStock() {
        Sale sale = activeSale(1L);
        sale.addItem(item(10L, "Milk", "10.00", 1));
        saleService.recalculateTotals(sale);
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(productClient.getProduct(10L)).thenReturn(product(10L, "Milk", "10.00", 5));
        when(customerClient.getCustomer(20L)).thenReturn(customer(20L, CreditStatus.PENDING));

        assertThatThrownBy(() -> saleService.checkout(1L, new CheckoutRequest(PaymentType.CREDIT, null, 20L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Customer credit status is not approved");

        verify(productClient, never()).decrementStock(any(), any(Integer.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void partialReturnRejectsQuantitiesGreaterThanRemainingPurchasedQuantity() {
        Sale sale = completedSale(1L);
        sale.addItem(item(10L, "Milk", "10.00", 5));
        when(saleRepository.findById(1L)).thenReturn(Optional.of(sale));
        when(returnItemRepository.sumReturnedQuantity(1L, 10L)).thenReturn(2);
        PartialReturnRequest request = new PartialReturnRequest(
                List.of(new ReturnItemRequest(10L, 4, "too many"))
        );

        assertThatThrownBy(() -> saleService.partialReturn(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Returned quantity cannot exceed purchased quantity");

        verify(productClient, never()).incrementStock(any(), any(Integer.class));
        verify(returnRepository, never()).save(any());
    }

    @Test
    void cancelExpiredFrozenSalesCancelsOnlyExpiredFrozenSales() {
        Sale expired = activeSale(1L);
        expired.setStatus(SaleState.FROZEN);
        expired.setFrozenAt(LocalDateTime.now().minusHours(3));
        when(saleRepository.findByStatusAndFrozenAtBefore(any(), any())).thenReturn(List.of(expired));

        saleService.cancelExpiredFrozenSales();

        assertThat(expired.getStatus()).isEqualTo(SaleState.CANCELLED);
        assertThat(expired.getCancellationReason()).isEqualTo("Frozen sale expired");
        assertThat(expired.getCancelledAt()).isNotNull();
        verify(saleRepository).save(expired);
    }

    private Sale activeSale(Long id) {
        Sale sale = new Sale();
        setId(sale, id);
        sale.setTerminalId("POS-1");
        sale.setCashierId("cashier-1");
        sale.setStatus(SaleState.ACTIVE);
        sale.setSubtotal(BigDecimal.ZERO);
        sale.setTax(BigDecimal.ZERO);
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTotal(BigDecimal.ZERO);
        sale.setCreatedAt(LocalDateTime.now());
        return sale;
    }

    private Sale completedSale(Long id) {
        Sale sale = activeSale(id);
        sale.setStatus(SaleState.COMPLETED);
        sale.setTransactionId("TX-1");
        sale.setCompletedAt(LocalDateTime.now());
        return sale;
    }

    private SaleItem item(Long productId, String name, String price, int quantity) {
        SaleItem item = new SaleItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setUnitPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        item.setLineTotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private ProductResponse product(Long id, String name, String price, int stock) {
        return new ProductResponse(id, name, "111", new BigDecimal(price), stock, "Dairy");
    }

    private CustomerResponse customer(Long id, CreditStatus creditStatus) {
        return new CustomerResponse(id, "Ana Customer", "CC", "123", creditStatus);
    }

    private void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
