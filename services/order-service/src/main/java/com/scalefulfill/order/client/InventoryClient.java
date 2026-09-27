package com.scalefulfill.order.client;

import com.scalefulfill.order.client.dto.InventoryReleaseRequest;
import com.scalefulfill.order.client.dto.InventoryReservationRequest;
import com.scalefulfill.order.client.dto.InventoryReservationResponse;
import com.scalefulfill.order.exception.DownstreamBusinessException;
import com.scalefulfill.order.exception.InventoryServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final RestClient restClient;

    @Value("${services.inventory.base-url:http://localhost:8082}")
    private String inventoryBaseUrl;

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveInventoryFallback")
    @Retry(name = "inventoryService")
    public InventoryReservationResponse reserveInventory(InventoryReservationRequest request) {
        log.info("[order-service -> inventory-service] Calling POST /api/v1/inventory/reservations for SKU [{}]",
                request.getProductId());

        try {
            return restClient.post()
                    .uri(inventoryBaseUrl + "/api/v1/inventory/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        String body = new String(resp.getBody().readAllBytes());
                        log.warn("[order-service] Inventory service returned 4xx: status={}, body={}",
                                resp.getStatusCode(), body);
                        throw new DownstreamBusinessException(resp.getStatusCode().value(), body);
                    })
                    .body(InventoryReservationResponse.class);

        } catch (DownstreamBusinessException e) {
            // Business rule rejection (e.g. 409 conflict insufficient stock) - do not trip circuit breaker
            throw e;
        } catch (Exception e) {
            log.error("[order-service] Failed communicating with inventory-service: {}", e.getMessage());
            throw new RestClientException("Downstream inventory service error: " + e.getMessage(), e);
        }
    }

    public InventoryReservationResponse reserveInventoryFallback(InventoryReservationRequest request, Throwable ex) {
        log.error("[RESILIENCE] Fallback invoked for product [{}] due to: [{}]",
                request.getProductId(), ex.getMessage());

        if (ex instanceof DownstreamBusinessException) {
            throw (DownstreamBusinessException) ex;
        }

        throw new InventoryServiceUnavailableException(
                "Inventory service is currently unavailable or circuit breaker is OPEN. Fast-failing.", ex);
    }

    public void releaseInventory(InventoryReleaseRequest request) {
        log.info("[order-service -> inventory-service] Releasing inventory for SKU [{}] at FC [{}]",
                request.getProductId(), request.getFulfillmentCenterId());
        try {
            restClient.post()
                    .uri(inventoryBaseUrl + "/api/v1/inventory/releases")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("[order-service] Warning: Failed to release inventory: {}", e.getMessage());
        }
    }
}
