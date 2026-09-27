package com.scalefulfill.search.controller;

import com.scalefulfill.search.dto.*;
import com.scalefulfill.search.model.OrderDocument;
import com.scalefulfill.search.model.ProductDocument;
import com.scalefulfill.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/orders")
    public ResponseEntity<OrderSearchResponse> searchOrders(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String fcId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "created_at") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        OrderSearchRequest request = OrderSearchRequest.builder()
                .query(query)
                .customerId(customerId)
                .status(status)
                .fcId(fcId)
                .minAmount(minAmount)
                .maxAmount(maxAmount)
                .fromDate(fromDate)
                .toDate(toDate)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortOrder(sortOrder)
                .build();

        return ResponseEntity.ok(searchService.searchOrders(request));
    }

    @GetMapping("/products")
    public ResponseEntity<ProductSearchResponse> searchProducts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String fcId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStockOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ProductSearchRequest request = ProductSearchRequest.builder()
                .query(query)
                .sku(sku)
                .fcId(fcId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .inStockOnly(inStockOnly)
                .page(page)
                .size(size)
                .build();

        return ResponseEntity.ok(searchService.searchProducts(request));
    }

    @PostMapping("/products")
    public ResponseEntity<Void> indexProduct(@RequestBody ProductDocument productDocument) {
        searchService.indexProduct(productDocument);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> indexOrder(@RequestBody OrderDocument orderDocument) {
        searchService.indexOrder(orderDocument);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/metrics")
    public ResponseEntity<SearchMetricsResponse> getMetrics() {
        return ResponseEntity.ok(searchService.getMetrics());
    }
}
