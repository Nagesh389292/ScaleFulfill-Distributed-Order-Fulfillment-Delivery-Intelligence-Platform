package com.scalefulfill.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scalefulfill.search.config.OpenSearchProperties;
import com.scalefulfill.search.dto.OrderSearchRequest;
import com.scalefulfill.search.dto.SearchMetricsResponse;
import com.scalefulfill.search.metrics.SearchMetrics;
import com.scalefulfill.search.model.OrderDocument;
import com.scalefulfill.search.model.ProductDocument;
import com.scalefulfill.search.service.impl.SearchServiceImpl;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private SearchMetrics searchMetrics;

    private OpenSearchProperties properties;
    private ObjectMapper objectMapper;
    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        properties = new OpenSearchProperties();
        properties.setHost("localhost");
        properties.setPort(9200);
        properties.setOrdersIndex("orders-index");
        properties.setProductsIndex("products-index");

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Timer noopTimer = org.mockito.Mockito.mock(Timer.class, org.mockito.Mockito.withSettings().lenient());
        org.mockito.Mockito.lenient().when(searchMetrics.getSearchQueryTimerOrders()).thenReturn(noopTimer);
        org.mockito.Mockito.lenient().when(searchMetrics.getSearchQueryTimerProducts()).thenReturn(noopTimer);

        searchService = new SearchServiceImpl(restClient, properties, objectMapper, searchMetrics);
    }

    @Test
    @DisplayName("Should track indexing lag metrics correctly")
    void shouldTrackIndexingLagMetrics() {
        searchService.recordIndexingLag(12);
        searchService.recordIndexingLag(18);

        SearchMetricsResponse metrics = searchService.getMetrics();
        assertNotNull(metrics);
        assertEquals(18, metrics.getLastMeasuredLagMs());
        assertEquals(15.0, metrics.getAvgIndexingLagMs());
        assertEquals("orders-index", metrics.getOrdersIndexName());
        assertEquals("products-index", metrics.getProductsIndexName());
    }

    @Test
    @DisplayName("Should build OrderDocument with nested items correctly")
    void shouldBuildOrderDocument() {
        OrderDocument doc = OrderDocument.builder()
                .orderId("ord-test-123")
                .customerId("cust-456")
                .status("CREATED")
                .totalAmount(new BigDecimal("149.99"))
                .createdAt(Instant.now())
                .items(List.of(
                        OrderDocument.OrderItemDoc.builder()
                                .productId("prod-001")
                                .productName("Mechanical Keyboard")
                                .quantity(1)
                                .unitPrice(new BigDecimal("149.99"))
                                .build()
                ))
                .build();

        assertNotNull(doc);
        assertEquals("ord-test-123", doc.getOrderId());
        assertEquals(1, doc.getItems().size());
        assertEquals("Mechanical Keyboard", doc.getItems().get(0).getProductName());
    }

    @Test
    @DisplayName("Should build ProductDocument correctly")
    void shouldBuildProductDocument() {
        ProductDocument doc = ProductDocument.builder()
                .productId("prod-001")
                .sku("SKU-KEYBOARD-RGB")
                .name("Mechanical Keyboard RGB")
                .price(new BigDecimal("129.99"))
                .availableQuantity(45)
                .fulfillmentCenters(List.of("FC-BLR-01", "FC-HYD-01"))
                .build();

        assertNotNull(doc);
        assertEquals("SKU-KEYBOARD-RGB", doc.getSku());
        assertEquals(2, doc.getFulfillmentCenters().size());
    }
}
