package com.scalefulfill.concurrency;

import com.scalefulfill.common.exception.InsufficientInventoryException;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.entity.FulfillmentCenter;
import com.scalefulfill.inventory.entity.Inventory;
import com.scalefulfill.inventory.entity.Product;
import com.scalefulfill.inventory.repository.FulfillmentCenterRepository;
import com.scalefulfill.inventory.repository.InventoryRepository;
import com.scalefulfill.inventory.repository.ProductRepository;
import com.scalefulfill.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Optimistic Locking High-Concurrency Reservation Tests")
class OptimisticLockingConcurrencyTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FulfillmentCenterRepository fulfillmentCenterRepository;

    private static final String CONCURRENT_PROD_ID = "PROD-CONCURRENT-01";
    private static final String CONCURRENT_FC_ID = "FC-BLR-01";
    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_THREADS = 20;

    @BeforeEach
    void setUp() {
        // Ensure FC exists
        if (!fulfillmentCenterRepository.existsById(CONCURRENT_FC_ID)) {
            fulfillmentCenterRepository.save(FulfillmentCenter.builder()
                    .id(CONCURRENT_FC_ID)
                    .code("BLR-01")
                    .name("Bangalore FC")
                    .latitude(12.9716)
                    .longitude(77.5946)
                    .active(true)
                    .createdAt(Instant.now())
                    .build());
        }

        // Save fresh test product
        productRepository.save(Product.builder()
                .id(CONCURRENT_PROD_ID)
                .sku("SKU-CONCURRENT-TEST")
                .name("Flash Sale Concurrent Item")
                .price(BigDecimal.valueOf(999.00))
                .weightKg(BigDecimal.valueOf(0.5))
                .createdAt(Instant.now())
                .build());

        // Initialize inventory with exactly 10 units
        inventoryRepository.findByProductIdAndFulfillmentCenterId(CONCURRENT_PROD_ID, CONCURRENT_FC_ID)
                .ifPresent(inventoryRepository::delete);

        inventoryRepository.save(Inventory.builder()
                .productId(CONCURRENT_PROD_ID)
                .fulfillmentCenterId(CONCURRENT_FC_ID)
                .availableQuantity(INITIAL_STOCK)
                .reservedQuantity(0)
                .version(0L)
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("20 concurrent requests on 10 units of stock must guarantee available inventory >= 0 and never oversell")
    void testConcurrentReservationsDoNotOversell() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(CONCURRENT_THREADS);

        AtomicInteger successfulReservations = new AtomicInteger(0);
        AtomicInteger insufficientStockFailures = new AtomicInteger(0);
        AtomicInteger concurrencyConflicts = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executor.submit(() -> {
                try {
                    // All threads await trigger to slam the database simultaneously
                    startGate.await();

                    ReservationRequest request = ReservationRequest.builder()
                            .productId(CONCURRENT_PROD_ID)
                            .fulfillmentCenterId(CONCURRENT_FC_ID)
                            .quantity(1) // each thread attempts to reserve 1 unit
                            .build();

                    inventoryService.reserveInventory(request);
                    successfulReservations.incrementAndGet();
                } catch (InsufficientInventoryException ex) {
                    insufficientStockFailures.incrementAndGet();
                } catch (OptimisticLockingFailureException ex) {
                    // Optimistic lock conflict detected: simultaneous transaction modified @Version
                    concurrencyConflicts.incrementAndGet();
                } catch (Exception ex) {
                    // Check cause for OptimisticLockingFailureException if wrapped
                    if (ex.getCause() instanceof OptimisticLockingFailureException) {
                        concurrencyConflicts.incrementAndGet();
                    } else {
                        ex.printStackTrace();
                    }
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent tasks should complete within 10 seconds");

        // Fetch final persisted inventory state from database
        Inventory finalInventory = inventoryRepository
                .findByProductIdAndFulfillmentCenterId(CONCURRENT_PROD_ID, CONCURRENT_FC_ID)
                .orElseThrow();

        System.out.printf("""
                ==========================================================
                CONCURRENCY TEST RESULTS:
                Initial Stock:                     %d
                Concurrent Threads:                %d
                Successful Reservations:           %d
                Insufficient Stock Rejections:     %d
                Optimistic Lock Conflicts:         %d
                Remaining Available Stock in DB:   %d
                Total Reserved Stock in DB:        %d
                ==========================================================
                %n""",
                INITIAL_STOCK,
                CONCURRENT_THREADS,
                successfulReservations.get(),
                insufficientStockFailures.get(),
                concurrencyConflicts.get(),
                finalInventory.getAvailableQuantity(),
                finalInventory.getReservedQuantity());

        // Invariants that MUST strictly hold:
        // 1. Available stock in database must never be negative
        assertTrue(finalInventory.getAvailableQuantity() >= 0,
                "Invariant violated: Available inventory dropped below 0!");

        // 2. Successful reservations must never exceed initial inventory
        assertTrue(successfulReservations.get() <= INITIAL_STOCK,
                "Invariant violated: Oversold inventory! More reservations succeeded than initial stock.");

        // 3. Persisted available + persisted reserved must equal initial stock
        assertEquals(INITIAL_STOCK, finalInventory.getAvailableQuantity() + finalInventory.getReservedQuantity(),
                "Stock conservation invariant violated!");

        // 4. Successful reservations must equal the reserved quantity in the database
        assertEquals(successfulReservations.get(), finalInventory.getReservedQuantity(),
                "Persisted reserved quantity does not match successful thread reservations");
    }
}
