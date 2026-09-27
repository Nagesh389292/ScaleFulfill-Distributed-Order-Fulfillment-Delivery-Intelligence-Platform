package com.scalefulfill.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "fulfillment_centers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentCenter {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at")
    private Instant createdAt;
}
