package com.scalefulfill.inventory.repository;

import com.scalefulfill.inventory.entity.FulfillmentCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FulfillmentCenterRepository extends JpaRepository<FulfillmentCenter, String> {
    Optional<FulfillmentCenter> findByCode(String code);
    List<FulfillmentCenter> findAllByActiveTrue();
}
