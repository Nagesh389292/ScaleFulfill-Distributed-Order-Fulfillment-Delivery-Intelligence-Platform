package com.scalefulfill.inventory.repository;

import com.scalefulfill.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findByProductId(String productId);

    Optional<Inventory> findByProductIdAndFulfillmentCenterId(String productId, String fulfillmentCenterId);

    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.availableQuantity > 0 ORDER BY i.availableQuantity DESC")
    List<Inventory> findAvailableByProductIdOrdered(@Param("productId") String productId);

    @Query("SELECT COALESCE(SUM(i.availableQuantity), 0) FROM Inventory i WHERE i.productId = :productId")
    Integer getTotalAvailableQuantity(@Param("productId") String productId);
}
