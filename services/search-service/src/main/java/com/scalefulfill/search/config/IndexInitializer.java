package com.scalefulfill.search.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitializer {

    private final RestClient openSearchRestClient;
    private final OpenSearchProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndices() {
        initIndex(properties.getOrdersIndex(), getOrdersIndexMapping());
        initIndex(properties.getProductsIndex(), getProductsIndexMapping());
    }

    public boolean initIndex(String indexName, String mappingJson) {
        try {
            boolean exists = Boolean.TRUE.equals(openSearchRestClient.get()
                    .uri("/" + indexName)
                    .exchange((req, res) -> res.getStatusCode().is2xxSuccessful()));

            if (exists) {
                log.info("OpenSearch index '{}' already exists.", indexName);
                return true;
            }
        } catch (Exception e) {
            log.warn("Index check for '{}' encountered exception, proceeding to create: {}", indexName, e.getMessage());
        }

        try {
            String response = openSearchRestClient.put()
                    .uri("/" + indexName)
                    .body(mappingJson)
                    .retrieve()
                    .body(String.class);

            log.info("Successfully created OpenSearch index '{}'. Response: {}", indexName, response);
            return true;
        } catch (Exception e) {
            log.error("Failed to create OpenSearch index '{}': {}", indexName, e.getMessage());
            return false;
        }
    }

    private String getOrdersIndexMapping() {
        return """
        {
          "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0,
            "analysis": {
              "analyzer": {
                "order_search_analyzer": {
                  "type": "custom",
                  "tokenizer": "standard",
                  "filter": ["lowercase", "trim"]
                }
              }
            }
          },
          "mappings": {
            "properties": {
              "order_id": { "type": "keyword" },
              "customer_id": { "type": "keyword" },
              "status": { "type": "keyword" },
              "total_amount": { "type": "double" },
              "assigned_fc_id": { "type": "keyword" },
              "created_at": { "type": "date" },
              "updated_at": { "type": "date" },
              "search_text": { 
                "type": "text",
                "analyzer": "order_search_analyzer"
              },
              "items": {
                "type": "nested",
                "properties": {
                  "product_id": { "type": "keyword" },
                  "product_name": { "type": "text" },
                  "quantity": { "type": "integer" },
                  "unit_price": { "type": "double" }
                }
              }
            }
          }
        }
        """;
    }

    private String getProductsIndexMapping() {
        return """
        {
          "settings": {
            "number_of_shards": 1,
            "number_of_replicas": 0
          },
          "mappings": {
            "properties": {
              "product_id": { "type": "keyword" },
              "sku": { "type": "keyword" },
              "name": {
                "type": "text",
                "fields": {
                  "keyword": { "type": "keyword", "ignore_above": 256 }
                }
              },
              "price": { "type": "double" },
              "weight_kg": { "type": "double" },
              "available_quantity": { "type": "integer" },
              "fulfillment_centers": { "type": "keyword" },
              "search_text": { "type": "text" }
            }
          }
        }
        """;
    }
}
