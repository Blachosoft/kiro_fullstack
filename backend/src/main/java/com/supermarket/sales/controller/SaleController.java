package com.supermarket.sales.controller;

import com.supermarket.sales.dto.request.AddItemRequest;
import com.supermarket.sales.dto.request.CancelRequest;
import com.supermarket.sales.dto.request.CheckoutRequest;
import com.supermarket.sales.dto.request.CreateSaleRequest;
import com.supermarket.sales.dto.request.FullReturnRequest;
import com.supermarket.sales.dto.request.PartialReturnRequest;
import com.supermarket.sales.dto.request.UpdateItemRequest;
import com.supermarket.sales.dto.response.CheckoutResponse;
import com.supermarket.sales.dto.response.FrozenSaleResponse;
import com.supermarket.sales.dto.response.ReturnResponse;
import com.supermarket.sales.dto.response.SaleResponse;
import com.supermarket.sales.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @Operation(summary = "Create a new active sale")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse createSale(@Valid @RequestBody CreateSaleRequest request) {
        return saleService.createSale(request);
    }

    @Operation(summary = "Get sale details")
    @GetMapping("/{saleId}")
    public SaleResponse getSale(@PathVariable Long saleId) {
        return saleService.getSale(saleId);
    }

    @Operation(summary = "Add an item to an active sale")
    @PostMapping("/{saleId}/items")
    public SaleResponse addItem(@PathVariable Long saleId, @Valid @RequestBody AddItemRequest request) {
        return saleService.addItem(saleId, request);
    }

    @Operation(summary = "Update an active sale item quantity")
    @PutMapping("/{saleId}/items/{itemId}")
    public SaleResponse updateItem(
            @PathVariable Long saleId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        return saleService.updateItem(saleId, itemId, request);
    }

    @Operation(summary = "Remove an item from an active sale")
    @DeleteMapping("/{saleId}/items/{itemId}")
    public SaleResponse removeItem(@PathVariable Long saleId, @PathVariable Long itemId) {
        return saleService.removeItem(saleId, itemId);
    }

    @Operation(summary = "Checkout an active sale")
    @PostMapping("/{saleId}/checkout")
    public CheckoutResponse checkout(@PathVariable Long saleId, @Valid @RequestBody CheckoutRequest request) {
        return saleService.checkout(saleId, request);
    }

    @Operation(summary = "Cancel an active or frozen sale")
    @PostMapping("/{saleId}/cancel")
    public SaleResponse cancel(@PathVariable Long saleId, @Valid @RequestBody CancelRequest request) {
        return saleService.cancel(saleId, request);
    }

    @Operation(summary = "Freeze an active sale")
    @PostMapping("/{saleId}/freeze")
    public SaleResponse freeze(@PathVariable Long saleId) {
        return saleService.freeze(saleId);
    }

    @Operation(summary = "Resume a frozen sale")
    @PostMapping("/{saleId}/resume")
    public SaleResponse resume(@PathVariable Long saleId) {
        return saleService.resume(saleId);
    }

    @Operation(summary = "List frozen sales by terminal")
    @GetMapping("/frozen")
    public List<FrozenSaleResponse> frozenSales(@RequestParam String terminalId) {
        return saleService.listFrozenSales(terminalId);
    }

    @Operation(summary = "Fully return a completed sale")
    @PostMapping({"/{saleId}/return", "/{saleId}/returns/full"})
    public ReturnResponse fullReturn(@PathVariable Long saleId, @Valid @RequestBody FullReturnRequest request) {
        return saleService.fullReturn(saleId, request);
    }

    @Operation(summary = "Partially return items from a completed sale")
    @PostMapping({"/{saleId}/partial-return", "/{saleId}/returns/partial"})
    public ReturnResponse partialReturn(@PathVariable Long saleId, @Valid @RequestBody PartialReturnRequest request) {
        return saleService.partialReturn(saleId, request);
    }
}
