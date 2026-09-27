package com.scalefulfill.search.service;

import com.scalefulfill.search.dto.*;
import com.scalefulfill.search.model.OrderDocument;
import com.scalefulfill.search.model.ProductDocument;

public interface SearchService {
    void indexOrder(OrderDocument orderDocument);
    void indexProduct(ProductDocument productDocument);
    OrderSearchResponse searchOrders(OrderSearchRequest request);
    ProductSearchResponse searchProducts(ProductSearchRequest request);
    SearchMetricsResponse getMetrics();
    void recordIndexingLag(long lagMs);
}
