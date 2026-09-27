package com.scalefulfill.search.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scalefulfill.search.config.OpenSearchProperties;
import com.scalefulfill.search.dto.*;
import com.scalefulfill.search.model.OrderDocument;
import com.scalefulfill.search.model.ProductDocument;
import com.scalefulfill.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final RestClient openSearchRestClient;
    private final OpenSearchProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicLong totalOrdersIndexed = new AtomicLong(0);
    private final AtomicLong totalProductsIndexed = new AtomicLong(0);
    private final AtomicLong lagMeasurementsCount = new AtomicLong(0);
    private final DoubleAdder totalLagAccumulator = new DoubleAdder();
    private volatile long lastMeasuredLagMs = 0;
    private volatile Instant lastIndexedAt = null;

    @Override
    public void indexOrder(OrderDocument orderDocument) {
        try {
            String json = objectMapper.writeValueAsString(orderDocument);
            String uri = "/" + properties.getOrdersIndex() + "/_doc/" + orderDocument.getOrderId() + "?refresh=true";

            openSearchRestClient.put()
                    .uri(uri)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();

            totalOrdersIndexed.incrementAndGet();
            lastIndexedAt = Instant.now();
            log.debug("Successfully indexed order '{}' into OpenSearch", orderDocument.getOrderId());
        } catch (Exception e) {
            log.error("Failed to index order '{}' into OpenSearch: {}", orderDocument.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("Failed to index order into OpenSearch: " + e.getMessage(), e);
        }
    }

    @Override
    public void indexProduct(ProductDocument productDocument) {
        try {
            String json = objectMapper.writeValueAsString(productDocument);
            String uri = "/" + properties.getProductsIndex() + "/_doc/" + productDocument.getProductId() + "?refresh=true";

            openSearchRestClient.put()
                    .uri(uri)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();

            totalProductsIndexed.incrementAndGet();
            log.debug("Successfully indexed product '{}' into OpenSearch", productDocument.getProductId());
        } catch (Exception e) {
            log.error("Failed to index product '{}' into OpenSearch: {}", productDocument.getProductId(), e.getMessage(), e);
            throw new RuntimeException("Failed to index product into OpenSearch: " + e.getMessage(), e);
        }
    }

    @Override
    public OrderSearchResponse searchOrders(OrderSearchRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("from", request.getPage() * request.getSize());
            root.put("size", request.getSize());

            ObjectNode queryNode = root.putObject("query");
            ObjectNode boolNode = queryNode.putObject("bool");
            ArrayNode mustArray = boolNode.putArray("must");
            ArrayNode filterArray = boolNode.putArray("filter");

            // Multi-match or wildcards if search text provided
            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                ObjectNode multiMatch = mustArray.addObject().putObject("multi_match");
                multiMatch.put("query", request.getQuery().trim());
                ArrayNode fields = multiMatch.putArray("fields");
                fields.add("order_id^3");
                fields.add("customer_id^2");
                fields.add("search_text");
                fields.add("items.product_name");
            } else {
                mustArray.addObject().putObject("match_all");
            }

            // Filters
            if (request.getCustomerId() != null && !request.getCustomerId().isBlank()) {
                filterArray.addObject().putObject("term").put("customer_id", request.getCustomerId().trim());
            }
            if (request.getStatus() != null && !request.getStatus().isBlank()) {
                filterArray.addObject().putObject("term").put("status", request.getStatus().trim());
            }
            if (request.getFcId() != null && !request.getFcId().isBlank()) {
                filterArray.addObject().putObject("term").put("assigned_fc_id", request.getFcId().trim());
            }

            // Amount Range Filter
            if (request.getMinAmount() != null || request.getMaxAmount() != null) {
                ObjectNode range = filterArray.addObject().putObject("range").putObject("total_amount");
                if (request.getMinAmount() != null) {
                    range.put("gte", request.getMinAmount().doubleValue());
                }
                if (request.getMaxAmount() != null) {
                    range.put("lte", request.getMaxAmount().doubleValue());
                }
            }

            // Date Range Filter
            if (request.getFromDate() != null || request.getToDate() != null) {
                ObjectNode range = filterArray.addObject().putObject("range").putObject("created_at");
                if (request.getFromDate() != null) {
                    range.put("gte", request.getFromDate().toString());
                }
                if (request.getToDate() != null) {
                    range.put("lte", request.getToDate().toString());
                }
            }

            // Sorting
            ArrayNode sortArray = root.putArray("sort");
            String sortField = request.getSortBy() != null ? request.getSortBy() : "created_at";
            String sortOrder = request.getSortOrder() != null ? request.getSortOrder().toLowerCase() : "desc";
            sortArray.addObject().putObject(sortField).put("order", sortOrder);

            // Aggregations
            ObjectNode aggs = root.putObject("aggs");
            aggs.putObject("status_breakdown")
                    .putObject("terms")
                    .put("field", "status");
            aggs.putObject("total_revenue")
                    .putObject("sum")
                    .put("field", "total_amount");

            String requestBody = objectMapper.writeValueAsString(root);
            String responseBody = openSearchRestClient.post()
                    .uri("/" + properties.getOrdersIndex() + "/_search")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            long executionTime = System.currentTimeMillis() - startTime;
            return parseOrderSearchResponse(responseBody, request.getPage(), request.getSize(), executionTime);
        } catch (Exception e) {
            log.error("Error executing order search: {}", e.getMessage(), e);
            throw new RuntimeException("Search execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ProductSearchResponse searchProducts(ProductSearchRequest request) {
        long startTime = System.currentTimeMillis();
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("from", request.getPage() * request.getSize());
            root.put("size", request.getSize());

            ObjectNode queryNode = root.putObject("query");
            ObjectNode boolNode = queryNode.putObject("bool");
            ArrayNode mustArray = boolNode.putArray("must");
            ArrayNode filterArray = boolNode.putArray("filter");

            if (request.getQuery() != null && !request.getQuery().isBlank()) {
                ObjectNode multiMatch = mustArray.addObject().putObject("multi_match");
                multiMatch.put("query", request.getQuery().trim());
                multiMatch.put("fuzziness", "AUTO");
                ArrayNode fields = multiMatch.putArray("fields");
                fields.add("sku^4");
                fields.add("name^3");
                fields.add("search_text");
            } else {
                mustArray.addObject().putObject("match_all");
            }

            if (request.getSku() != null && !request.getSku().isBlank()) {
                filterArray.addObject().putObject("term").put("sku", request.getSku().trim());
            }
            if (request.getFcId() != null && !request.getFcId().isBlank()) {
                filterArray.addObject().putObject("term").put("fulfillment_centers", request.getFcId().trim());
            }
            if (Boolean.TRUE.equals(request.getInStockOnly())) {
                filterArray.addObject().putObject("range").putObject("available_quantity").put("gt", 0);
            }
            if (request.getMinPrice() != null || request.getMaxPrice() != null) {
                ObjectNode range = filterArray.addObject().putObject("range").putObject("price");
                if (request.getMinPrice() != null) {
                    range.put("gte", request.getMinPrice().doubleValue());
                }
                if (request.getMaxPrice() != null) {
                    range.put("lte", request.getMaxPrice().doubleValue());
                }
            }

            String requestBody = objectMapper.writeValueAsString(root);
            String responseBody = openSearchRestClient.post()
                    .uri("/" + properties.getProductsIndex() + "/_search")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            long executionTime = System.currentTimeMillis() - startTime;
            return parseProductSearchResponse(responseBody, request.getPage(), request.getSize(), executionTime);
        } catch (Exception e) {
            log.error("Error executing product search: {}", e.getMessage(), e);
            throw new RuntimeException("Search execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SearchMetricsResponse getMetrics() {
        String clusterStatus = "UNKNOWN";
        try {
            String healthJson = openSearchRestClient.get()
                    .uri("/_cluster/health")
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(healthJson);
            clusterStatus = node.path("status").asText("UNKNOWN");
        } catch (Exception e) {
            clusterStatus = "UNREACHABLE";
        }

        long count = lagMeasurementsCount.get();
        double avgLag = count > 0 ? totalLagAccumulator.sum() / count : 0.0;

        return SearchMetricsResponse.builder()
                .totalOrdersIndexed(totalOrdersIndexed.get())
                .totalProductsIndexed(totalProductsIndexed.get())
                .avgIndexingLagMs(Math.round(avgLag * 100.0) / 100.0)
                .lastMeasuredLagMs(lastMeasuredLagMs)
                .clusterStatus(clusterStatus)
                .lastIndexedAt(lastIndexedAt)
                .ordersIndexName(properties.getOrdersIndex())
                .productsIndexName(properties.getProductsIndex())
                .build();
    }

    @Override
    public void recordIndexingLag(long lagMs) {
        lastMeasuredLagMs = lagMs;
        totalLagAccumulator.add(lagMs);
        lagMeasurementsCount.incrementAndGet();
    }

    private OrderSearchResponse parseOrderSearchResponse(String json, int page, int size, long executionTime) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        long totalHits = root.path("hits").path("total").path("value").asLong(0);

        List<OrderDocument> orders = new ArrayList<>();
        JsonNode hitsArray = root.path("hits").path("hits");
        if (hitsArray.isArray()) {
            for (JsonNode hit : hitsArray) {
                OrderDocument doc = objectMapper.treeToValue(hit.path("_source"), OrderDocument.class);
                orders.add(doc);
            }
        }

        Map<String, Long> statusAgg = new LinkedHashMap<>();
        JsonNode statusBuckets = root.path("aggregations").path("status_breakdown").path("buckets");
        if (statusBuckets.isArray()) {
            for (JsonNode bucket : statusBuckets) {
                statusAgg.put(bucket.path("key").asText(), bucket.path("doc_count").asLong());
            }
        }

        Double totalRevenue = null;
        JsonNode revenueNode = root.path("aggregations").path("total_revenue").path("value");
        if (!revenueNode.isMissingNode() && !revenueNode.isNull()) {
            totalRevenue = revenueNode.asDouble();
        }

        return OrderSearchResponse.builder()
                .results(orders)
                .totalHits(totalHits)
                .page(page)
                .size(size)
                .executionTimeMs(executionTime)
                .statusAggregation(statusAgg)
                .totalAmountSum(totalRevenue)
                .build();
    }

    private ProductSearchResponse parseProductSearchResponse(String json, int page, int size, long executionTime) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        long totalHits = root.path("hits").path("total").path("value").asLong(0);

        List<ProductDocument> products = new ArrayList<>();
        JsonNode hitsArray = root.path("hits").path("hits");
        if (hitsArray.isArray()) {
            for (JsonNode hit : hitsArray) {
                ProductDocument doc = objectMapper.treeToValue(hit.path("_source"), ProductDocument.class);
                products.add(doc);
            }
        }

        return ProductSearchResponse.builder()
                .results(products)
                .totalHits(totalHits)
                .page(page)
                .size(size)
                .executionTimeMs(executionTime)
                .build();
    }
}
