package com.supermarket.sales.service;

import com.supermarket.sales.client.CustomerClient;
import com.supermarket.sales.client.ProductClient;
import com.supermarket.sales.config.SalesProperties;
import com.supermarket.sales.domain.CreditStatus;
import com.supermarket.sales.domain.Payment;
import com.supermarket.sales.domain.PaymentType;
import com.supermarket.sales.domain.Return;
import com.supermarket.sales.domain.ReturnItem;
import com.supermarket.sales.domain.ReturnType;
import com.supermarket.sales.domain.Sale;
import com.supermarket.sales.domain.SaleItem;
import com.supermarket.sales.domain.SaleState;
import com.supermarket.sales.dto.request.AddItemRequest;
import com.supermarket.sales.dto.request.CancelRequest;
import com.supermarket.sales.dto.request.CheckoutRequest;
import com.supermarket.sales.dto.request.CreateSaleRequest;
import com.supermarket.sales.dto.request.FullReturnRequest;
import com.supermarket.sales.dto.request.PartialReturnRequest;
import com.supermarket.sales.dto.request.ReturnItemRequest;
import com.supermarket.sales.dto.request.UpdateItemRequest;
import com.supermarket.sales.dto.response.CheckoutResponse;
import com.supermarket.sales.dto.response.CustomerResponse;
import com.supermarket.sales.dto.response.FrozenSaleResponse;
import com.supermarket.sales.dto.response.ProductResponse;
import com.supermarket.sales.dto.response.ReceiptItemResponse;
import com.supermarket.sales.dto.response.ReceiptResponse;
import com.supermarket.sales.dto.response.ReturnResponse;
import com.supermarket.sales.dto.response.SaleResponse;
import com.supermarket.sales.exception.BusinessRuleException;
import com.supermarket.sales.exception.ConflictException;
import com.supermarket.sales.exception.ResourceNotFoundException;
import com.supermarket.sales.mapper.SaleMapper;
import com.supermarket.sales.repository.PaymentRepository;
import com.supermarket.sales.repository.ReturnItemRepository;
import com.supermarket.sales.repository.ReturnRepository;
import com.supermarket.sales.repository.SaleItemRepository;
import com.supermarket.sales.repository.SaleRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PaymentRepository paymentRepository;
    private final ReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final ProductClient productClient;
    private final CustomerClient customerClient;
    private final FinancialCalculator financialCalculator;
    private final SalesProperties salesProperties;
    private final SaleMapper saleMapper;

    public SaleService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            PaymentRepository paymentRepository,
            ReturnRepository returnRepository,
            ReturnItemRepository returnItemRepository,
            ProductClient productClient,
            CustomerClient customerClient,
            FinancialCalculator financialCalculator,
            SalesProperties salesProperties,
            SaleMapper saleMapper
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.paymentRepository = paymentRepository;
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.productClient = productClient;
        this.customerClient = customerClient;
        this.financialCalculator = financialCalculator;
        this.salesProperties = salesProperties;
        this.saleMapper = saleMapper;
    }

    @Transactional
    public SaleResponse createSale(CreateSaleRequest request) {
        Sale sale = new Sale();
        sale.setTerminalId(request.terminalId());
        sale.setCashierId(request.cashierId());
        sale.setCustomerId(request.customerId());
        sale.setStatus(SaleState.ACTIVE);
        sale.setSubtotal(moneyZero());
        sale.setTax(moneyZero());
        sale.setDiscount(moneyZero());
        sale.setTotal(moneyZero());
        sale.setCreatedAt(LocalDateTime.now());
        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Transactional
    public SaleResponse getSale(Long saleId) {
        return saleMapper.toResponse(findSale(saleId));
    }

    @Transactional
    public SaleResponse addItem(Long saleId, AddItemRequest request) {
        Sale sale = findSale(saleId);
        requireActive(sale);

        ProductResponse product = productClient.getProduct(request.productId());
        SaleItem existingItem = sale.getItems().stream()
                .filter(item -> item.getProductId().equals(request.productId()))
                .findFirst()
                .orElse(null);

        int requestedTotalQuantity = request.quantity();
        if (existingItem != null) {
            requestedTotalQuantity += existingItem.getQuantity();
        }
        validateStock(product, requestedTotalQuantity);

        if (existingItem == null) {
            SaleItem item = new SaleItem();
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setUnitPrice(financialCalculator.money(product.unitPrice()));
            item.setQuantity(request.quantity());
            item.setLineTotal(financialCalculator.calculateLineTotal(product.unitPrice(), request.quantity()));
            sale.addItem(item);
        } else {
            existingItem.setQuantity(requestedTotalQuantity);
            existingItem.setUnitPrice(financialCalculator.money(product.unitPrice()));
            existingItem.setLineTotal(financialCalculator.calculateLineTotal(product.unitPrice(), requestedTotalQuantity));
        }

        recalculateTotals(sale);
        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Transactional
    public SaleResponse updateItem(Long saleId, Long itemId, UpdateItemRequest request) {
        Sale sale = findSale(saleId);
        requireActive(sale);
        SaleItem item = saleItemRepository.findBySaleIdAndId(saleId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale item not found"));
        ProductResponse product = productClient.getProduct(item.getProductId());
        validateStock(product, request.quantity());
        item.setUnitPrice(financialCalculator.money(product.unitPrice()));
        item.setQuantity(request.quantity());
        item.setLineTotal(financialCalculator.calculateLineTotal(product.unitPrice(), request.quantity()));
        recalculateTotals(sale);
        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Transactional
    public SaleResponse removeItem(Long saleId, Long itemId) {
        Sale sale = findSale(saleId);
        requireActive(sale);
        SaleItem item = saleItemRepository.findBySaleIdAndId(saleId, itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale item not found"));
        sale.removeItem(item);
        recalculateTotals(sale);
        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Transactional
    public CheckoutResponse checkout(Long saleId, CheckoutRequest request) {
        Sale sale = findSale(saleId);
        requireActive(sale);
        if (sale.getItems().isEmpty()) {
            throw new BusinessRuleException("Cannot checkout an empty sale");
        }

        List<String> outOfStockItems = sale.getItems().stream()
                .filter(item -> {
                    ProductResponse product = productClient.getProduct(item.getProductId());
                    return product.availableStock() == null || item.getQuantity() > product.availableStock();
                })
                .map(SaleItem::getProductName)
                .toList();
        if (!outOfStockItems.isEmpty()) {
            throw new ConflictException("Insufficient stock for: " + String.join(", ", outOfStockItems));
        }

        Payment payment = buildPayment(sale, request);
        sale.getItems().forEach(item -> productClient.decrementStock(item.getProductId(), item.getQuantity()));

        sale.setStatus(SaleState.COMPLETED);
        sale.setCompletedAt(LocalDateTime.now());
        sale.setTransactionId("TX-" + UUID.randomUUID());
        saleRepository.save(sale);
        paymentRepository.save(payment);

        ReceiptResponse receipt = receiptForSale(sale, payment, null, null);
        return new CheckoutResponse(sale.getTransactionId(), receipt, payment.getChangeAmount(), payment.getCreditReferenceNumber());
    }

    @Transactional
    public SaleResponse cancel(Long saleId, CancelRequest request) {
        Sale sale = findSale(saleId);
        if (sale.getStatus() != SaleState.ACTIVE && sale.getStatus() != SaleState.FROZEN) {
            throw new BusinessRuleException("Only active or frozen sales can be cancelled");
        }
        sale.setStatus(SaleState.CANCELLED);
        sale.setCancelledAt(LocalDateTime.now());
        sale.setCancellationReason(request.reason());
        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Transactional
    public SaleResponse freeze(Long saleId) {
        Sale sale = findSale(saleId);
        requireActive(sale);
        sale.setStatus(SaleState.FROZEN);
        sale.setFrozenAt(LocalDateTime.now());
        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Transactional
    public SaleResponse resume(Long saleId) {
        Sale sale = findSale(saleId);
        if (sale.getStatus() != SaleState.FROZEN) {
            throw new BusinessRuleException("Only frozen sales can be resumed");
        }
        sale.setStatus(SaleState.ACTIVE);
        sale.setResumedAt(LocalDateTime.now());
        return saleMapper.toResponse(saleRepository.save(sale));
    }

    @Transactional
    public List<FrozenSaleResponse> listFrozenSales(String terminalId) {
        return saleRepository.findByTerminalIdAndStatusOrderByFrozenAtDesc(terminalId, SaleState.FROZEN)
                .stream()
                .map(sale -> new FrozenSaleResponse(
                        sale.getId(),
                        sale.getCustomerId() == null ? null : "Customer " + sale.getCustomerId(),
                        sale.getItems().stream().mapToInt(SaleItem::getQuantity).sum(),
                        sale.getTotal(),
                        sale.getFrozenAt()
                ))
                .toList();
    }

    @Transactional
    public ReturnResponse fullReturn(Long saleId, FullReturnRequest request) {
        Sale sale = findSale(saleId);
        requireReturnable(sale, true);

        Return returnRecord = createReturn(sale, ReturnType.FULL);
        for (SaleItem item : sale.getItems()) {
            int alreadyReturned = returnItemRepository.sumReturnedQuantity(saleId, item.getProductId());
            int remaining = item.getQuantity() - alreadyReturned;
            if (remaining > 0) {
                addReturnItem(returnRecord, item.getProductId(), remaining, request.reason());
                productClient.incrementStock(item.getProductId(), remaining);
            }
        }

        sale.setStatus(SaleState.RETURNED);
        returnRepository.save(returnRecord);
        saleRepository.save(sale);
        return new ReturnResponse(returnRecord.getReturnId(), receiptForReturn(sale, returnRecord));
    }

    @Transactional
    public ReturnResponse partialReturn(Long saleId, PartialReturnRequest request) {
        Sale sale = findSale(saleId);
        requireReturnable(sale, false);

        Return returnRecord = createReturn(sale, ReturnType.PARTIAL);
        for (ReturnItemRequest itemRequest : request.items()) {
            SaleItem original = sale.getItems().stream()
                    .filter(item -> item.getProductId().equals(itemRequest.productId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Sale item not found for return"));
            int alreadyReturned = returnItemRepository.sumReturnedQuantity(saleId, itemRequest.productId());
            int remaining = original.getQuantity() - alreadyReturned;
            if (itemRequest.quantity() > remaining) {
                throw new BusinessRuleException("Returned quantity cannot exceed purchased quantity");
            }
            addReturnItem(returnRecord, itemRequest.productId(), itemRequest.quantity(), itemRequest.reason());
            productClient.incrementStock(itemRequest.productId(), itemRequest.quantity());
        }

        returnRepository.save(returnRecord);
        sale.setStatus(allItemsReturned(sale) ? SaleState.RETURNED : SaleState.PARTIALLY_RETURNED);
        saleRepository.save(sale);
        return new ReturnResponse(returnRecord.getReturnId(), receiptForReturn(sale, returnRecord));
    }

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void cancelExpiredFrozenSales() {
        LocalDateTime expirationCutoff = LocalDateTime.now().minus(salesProperties.freezeExpiration());
        saleRepository.findByStatusAndFrozenAtBefore(SaleState.FROZEN, expirationCutoff).forEach(sale -> {
            sale.setStatus(SaleState.CANCELLED);
            sale.setCancelledAt(LocalDateTime.now());
            sale.setCancellationReason("Frozen sale expired");
            saleRepository.save(sale);
        });
    }

    public Sale findSale(Long saleId) {
        return saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));
    }

    public void requireActive(Sale sale) {
        if (sale.getStatus() != SaleState.ACTIVE) {
            throw new BusinessRuleException("Cannot modify sale in current state");
        }
    }

    public void recalculateTotals(Sale sale) {
        BigDecimal subtotal = financialCalculator.calculateSubtotal(sale.getItems());
        BigDecimal tax = financialCalculator.calculateTax(subtotal, salesProperties.taxRate());
        BigDecimal total = financialCalculator.calculateTotal(subtotal, tax, sale.getDiscount());
        sale.setSubtotal(subtotal);
        sale.setTax(tax);
        sale.setTotal(total);
    }

    private void validateStock(ProductResponse product, int requestedQuantity) {
        if (product.availableStock() == null || requestedQuantity > product.availableStock()) {
            throw new ConflictException("Insufficient stock for product");
        }
    }

    private Payment buildPayment(Sale sale, CheckoutRequest request) {
        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setPaymentType(request.paymentType());
        payment.setPaymentDate(LocalDateTime.now());

        if (request.paymentType() == PaymentType.CASH) {
            BigDecimal amountReceived = financialCalculator.money(request.amountReceived() == null ? BigDecimal.ZERO : request.amountReceived());
            if (amountReceived.compareTo(sale.getTotal()) < 0) {
                throw new BusinessRuleException("Amount received must be greater than or equal to sale total");
            }
            payment.setAmountReceived(amountReceived);
            payment.setChangeAmount(financialCalculator.calculateChange(amountReceived, sale.getTotal()));
            return payment;
        }

        Long customerId = request.customerId() != null ? request.customerId() : sale.getCustomerId();
        if (customerId == null) {
            throw new BusinessRuleException("Customer is mandatory for credit sales");
        }
        CustomerResponse customer = customerClient.getCustomer(customerId);
        if (customer.creditStatus() != CreditStatus.APPROVED) {
            throw new BusinessRuleException("Customer credit status is not approved");
        }
        sale.setCustomerId(customerId);
        payment.setAmountReceived(sale.getTotal());
        payment.setChangeAmount(moneyZero());
        payment.setCreditReferenceNumber("CR-" + UUID.randomUUID());
        return payment;
    }

    private void requireReturnable(Sale sale, boolean fullReturn) {
        if (fullReturn && sale.getStatus() != SaleState.COMPLETED) {
            throw new BusinessRuleException("Only completed sales can be fully returned");
        }
        if (!fullReturn && sale.getStatus() != SaleState.COMPLETED && sale.getStatus() != SaleState.PARTIALLY_RETURNED) {
            throw new BusinessRuleException("Only completed sales can be partially returned");
        }
    }

    private Return createReturn(Sale sale, ReturnType type) {
        Return returnRecord = new Return();
        returnRecord.setOriginalSale(sale);
        returnRecord.setReturnType(type);
        returnRecord.setReturnId("RT-" + UUID.randomUUID());
        returnRecord.setReturnDate(LocalDateTime.now());
        return returnRecord;
    }

    private void addReturnItem(Return returnRecord, Long productId, int quantity, String reason) {
        ReturnItem item = new ReturnItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setReason(reason);
        returnRecord.addItem(item);
    }

    private boolean allItemsReturned(Sale sale) {
        return sale.getItems().stream()
                .allMatch(item -> returnItemRepository.sumReturnedQuantity(sale.getId(), item.getProductId()) >= item.getQuantity());
    }

    private ReceiptResponse receiptForSale(Sale sale, Payment payment, String originalTransactionId, String refundType) {
        List<ReceiptItemResponse> items = sale.getItems().stream()
                .map(item -> new ReceiptItemResponse(item.getProductId(), item.getProductName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal()))
                .toList();
        return new ReceiptResponse(
                "RC-" + UUID.randomUUID(),
                salesProperties.storeName(),
                sale.getTerminalId(),
                sale.getCashierId(),
                LocalDateTime.now(),
                sale.getCustomerId(),
                items,
                sale.getSubtotal(),
                sale.getTax(),
                sale.getDiscount(),
                sale.getTotal(),
                payment.getPaymentType(),
                payment.getAmountReceived(),
                payment.getChangeAmount(),
                sale.getTransactionId(),
                payment.getCreditReferenceNumber(),
                originalTransactionId,
                refundType
        );
    }

    private ReceiptResponse receiptForReturn(Sale sale, Return returnRecord) {
        Payment originalPayment = paymentRepository.findBySaleId(sale.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Original payment not found"));
        List<ReceiptItemResponse> items = returnRecord.getItems().stream()
                .map(returnItem -> {
                    SaleItem original = sale.getItems().stream()
                            .filter(item -> item.getProductId().equals(returnItem.getProductId()))
                            .findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("Original sale item not found"));
                    return new ReceiptItemResponse(
                            original.getProductId(),
                            original.getProductName(),
                            original.getUnitPrice(),
                            returnItem.getQuantity(),
                            financialCalculator.calculateLineTotal(original.getUnitPrice(), returnItem.getQuantity())
                    );
                })
                .toList();
        BigDecimal subtotal = items.stream().map(ReceiptItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = financialCalculator.calculateTax(subtotal, salesProperties.taxRate());
        BigDecimal total = financialCalculator.calculateTotal(subtotal, tax, BigDecimal.ZERO);
        return new ReceiptResponse(
                returnRecord.getReturnId(),
                salesProperties.storeName(),
                sale.getTerminalId(),
                sale.getCashierId(),
                returnRecord.getReturnDate(),
                sale.getCustomerId(),
                items,
                subtotal,
                tax,
                BigDecimal.ZERO,
                total,
                originalPayment.getPaymentType(),
                total,
                BigDecimal.ZERO,
                returnRecord.getReturnId(),
                null,
                sale.getTransactionId(),
                originalPayment.getPaymentType() == PaymentType.CREDIT ? "CREDIT_NOTE" : "CASH_REFUND"
        );
    }

    private BigDecimal moneyZero() {
        return financialCalculator.money(BigDecimal.ZERO);
    }
}
