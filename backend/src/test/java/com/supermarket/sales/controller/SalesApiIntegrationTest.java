package com.supermarket.sales.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.sales.client.CustomerClient;
import com.supermarket.sales.client.ProductClient;
import com.supermarket.sales.domain.CreditStatus;
import com.supermarket.sales.domain.Sale;
import com.supermarket.sales.domain.SaleItem;
import com.supermarket.sales.domain.SaleState;
import com.supermarket.sales.dto.response.CustomerResponse;
import com.supermarket.sales.dto.response.ProductResponse;
import com.supermarket.sales.repository.PaymentRepository;
import com.supermarket.sales.repository.ReturnItemRepository;
import com.supermarket.sales.repository.ReturnRepository;
import com.supermarket.sales.repository.SaleRepository;
import com.supermarket.sales.service.SaleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SalesApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private ReturnItemRepository returnItemRepository;

    @Autowired
    private SaleService saleService;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private CustomerClient customerClient;

    @BeforeEach
    void setUp() {
        reset(productClient, customerClient);
        returnItemRepository.deleteAll();
        returnRepository.deleteAll();
        paymentRepository.deleteAll();
        saleRepository.deleteAll();

        when(productClient.getProduct(1L)).thenReturn(product(1L, "Milk", "111", "Dairy", "10.00", 20));
        when(productClient.getProduct(2L)).thenReturn(product(2L, "Rice", "222", "Pantry", "5.00", 20));
        when(productClient.searchByName("mi")).thenReturn(List.of(product(1L, "Milk", "111", "Dairy", "10.00", 20)));
        when(productClient.searchByBarcode("111")).thenReturn(product(1L, "Milk", "111", "Dairy", "10.00", 20));
        doNothing().when(productClient).decrementStock(anyLong(), org.mockito.ArgumentMatchers.anyInt());
        doNothing().when(productClient).incrementStock(anyLong(), org.mockito.ArgumentMatchers.anyInt());

        when(customerClient.getCustomer(10L)).thenReturn(customer(10L, CreditStatus.APPROVED));
        when(customerClient.getCustomer(11L)).thenReturn(customer(11L, CreditStatus.REJECTED));
        when(customerClient.searchByName("ana")).thenReturn(List.of(customer(10L, CreditStatus.APPROVED)));
        when(customerClient.searchByDocumentNumber("123")).thenReturn(customer(10L, CreditStatus.APPROVED));
    }

    @Test
    void productAndCustomerSearchEndpointsProxyExternalApis() throws Exception {
        mockMvc.perform(get("/api/products/search").param("name", "mi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Milk"));

        mockMvc.perform(get("/api/products/search").param("barcode", "111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("111"));

        mockMvc.perform(get("/api/customers/search").param("name", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].creditStatus").value("APPROVED"));

        mockMvc.perform(get("/api/customers/search").param("documentNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentNumber").value("123"));

        mockMvc.perform(get("/api/products/search"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Either name or barcode is required"));
    }

    @Test
    void cashSaleCompletesAndGeneratesReceipt() throws Exception {
        long saleId = createSale(null);

        mockMvc.perform(post("/api/sales/{saleId}/items", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.subtotal").value(20.00))
                .andExpect(jsonPath("$.tax").value(3.80))
                .andExpect(jsonPath("$.total").value(23.80));

        mockMvc.perform(post("/api/sales/{saleId}/items", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(5));

        mockMvc.perform(post("/api/sales/{saleId}/checkout", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"CASH\",\"amountReceived\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId", containsString("TX-")))
                .andExpect(jsonPath("$.receipt.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.receipt.items[0].productName").value("Milk"))
                .andExpect(jsonPath("$.change").value(0.50));

        mockMvc.perform(get("/api/sales/{saleId}", saleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(productClient).decrementStock(1L, 5);
    }

    @Test
    void itemUpdatesRemovalAndValidationErrorsWork() throws Exception {
        long saleId = createSale(null);
        long itemId = addItem(saleId, 1L, 2);

        mockMvc.perform(put("/api/sales/{saleId}/items/{itemId}", saleId, itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4));

        mockMvc.perform(delete("/api/sales/{saleId}/items/{itemId}", saleId, itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mockMvc.perform(post("/api/sales/{saleId}/checkout", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"CASH\",\"amountReceived\":100}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cannot checkout an empty sale"));

        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminalId\":\"\",\"cashierId\":\"cashier-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void checkoutRejectsUnderpaymentInsufficientStockAndRejectedCredit() throws Exception {
        long underpaidSale = createSale(null);
        addItem(underpaidSale, 1L, 1);
        mockMvc.perform(post("/api/sales/{saleId}/checkout", underpaidSale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"CASH\",\"amountReceived\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Amount received must be greater than or equal to sale total"));

        when(productClient.getProduct(2L)).thenReturn(product(2L, "Rice", "222", "Pantry", "5.00", 1));
        long stockSale = createSale(null);
        mockMvc.perform(post("/api/sales/{saleId}/items", stockSale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2,\"quantity\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock for product"));

        long creditSale = createSale(11L);
        addItem(creditSale, 1L, 1);
        mockMvc.perform(post("/api/sales/{saleId}/checkout", creditSale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"CREDIT\",\"customerId\":11}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Customer credit status is not approved"));
    }

    @Test
    void creditSaleCompletesWithReference() throws Exception {
        long saleId = createSale(10L);
        addItem(saleId, 2L, 2);

        mockMvc.perform(post("/api/sales/{saleId}/checkout", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"CREDIT\",\"customerId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditReferenceNumber", containsString("CR-")))
                .andExpect(jsonPath("$.receipt.paymentMethod").value("CREDIT"))
                .andExpect(jsonPath("$.receipt.creditReferenceNumber", containsString("CR-")));
    }

    @Test
    void freezeResumeListAndCancelFlowsFollowStateRules() throws Exception {
        long saleId = createSale(null);
        addItem(saleId, 1L, 1);

        mockMvc.perform(post("/api/sales/{saleId}/freeze", saleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        mockMvc.perform(get("/api/sales/frozen").param("terminalId", "POS-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saleId").value(saleId))
                .andExpect(jsonPath("$[0].itemCount").value(1));

        mockMvc.perform(post("/api/sales/{saleId}/resume", saleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/sales/{saleId}/cancel", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"customer changed mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("customer changed mind"));

        mockMvc.perform(post("/api/sales/{saleId}/items", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cannot modify sale in current state"));
    }

    @Test
    void scheduledExpirationCancelsOldFrozenSales() {
        Sale sale = new Sale();
        sale.setTerminalId("POS-1");
        sale.setCashierId("cashier-1");
        sale.setStatus(SaleState.FROZEN);
        sale.setSubtotal(BigDecimal.ZERO);
        sale.setTax(BigDecimal.ZERO);
        sale.setDiscount(BigDecimal.ZERO);
        sale.setTotal(BigDecimal.ZERO);
        sale.setCreatedAt(LocalDateTime.now().minusHours(3));
        sale.setFrozenAt(LocalDateTime.now().minusHours(3));
        Sale saved = saleRepository.save(sale);

        saleService.cancelExpiredFrozenSales();

        Sale expired = saleRepository.findById(saved.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(expired.getStatus()).isEqualTo(SaleState.CANCELLED);
        org.assertj.core.api.Assertions.assertThat(expired.getCancellationReason()).isEqualTo("Frozen sale expired");
    }

    @Test
    void fullReturnRestoresStockAndPreventsReturningReturnedSale() throws Exception {
        long saleId = completedCashSale(1L, 2);

        mockMvc.perform(post("/api/sales/{saleId}/return", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"damaged package\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnId", containsString("RT-")))
                .andExpect(jsonPath("$.returnReceipt.originalTransactionId", containsString("TX-")))
                .andExpect(jsonPath("$.returnReceipt.refundType").value("CASH_REFUND"));

        verify(productClient).incrementStock(1L, 2);

        mockMvc.perform(post("/api/sales/{saleId}/return", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"again\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Only completed sales can be fully returned"));
    }

    @Test
    void partialReturnCanBeRepeatedForRemainingQuantities() throws Exception {
        long saleId = completedCashSale(1L, 5);

        mockMvc.perform(post("/api/sales/{saleId}/partial-return", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":2,\"reason\":\"opened\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnReceipt.items[0].quantity").value(2))
                .andExpect(jsonPath("$.returnReceipt.refundType").value("CASH_REFUND"));

        mockMvc.perform(post("/api/sales/{saleId}/partial-return", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":4,\"reason\":\"too much\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Returned quantity cannot exceed purchased quantity"));

        mockMvc.perform(post("/api/sales/{saleId}/partial-return", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":3,\"reason\":\"remaining\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnReceipt.items[0].quantity").value(3));
    }

    @Test
    void creditReturnsGenerateCreditNotes() throws Exception {
        long saleId = createSale(10L);
        addItem(saleId, 1L, 1);
        mockMvc.perform(post("/api/sales/{saleId}/checkout", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"CREDIT\",\"customerId\":10}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sales/{saleId}/partial-return", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":1,\"quantity\":1,\"reason\":\"credit return\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnReceipt.refundType").value("CREDIT_NOTE"));
    }

    private long completedCashSale(Long productId, int quantity) throws Exception {
        long saleId = createSale(null);
        addItem(saleId, productId, quantity);
        mockMvc.perform(post("/api/sales/{saleId}/checkout", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentType\":\"CASH\",\"amountReceived\":1000}"))
                .andExpect(status().isOk());
        return saleId;
    }

    private long createSale(Long customerId) throws Exception {
        String customerJson = customerId == null ? "" : ",\"customerId\":" + customerId;
        MvcResult result = mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"terminalId\":\"POS-1\",\"cashierId\":\"cashier-1\"" + customerJson + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private long addItem(long saleId, Long productId, int quantity) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sales/{saleId}/items", saleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("items").get(body.get("items").size() - 1).get("id").asLong();
    }

    private ProductResponse product(Long id, String name, String barcode, String category, String price, int stock) {
        return new ProductResponse(id, name, barcode, new BigDecimal(price), stock, category);
    }

    private CustomerResponse customer(Long id, CreditStatus creditStatus) {
        return new CustomerResponse(id, "Ana Customer", "CC", "123", creditStatus);
    }
}
