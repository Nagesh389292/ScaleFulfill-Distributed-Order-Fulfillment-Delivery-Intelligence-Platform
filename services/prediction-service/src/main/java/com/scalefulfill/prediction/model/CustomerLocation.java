package com.scalefulfill.prediction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerLocation {
    private String customerId;
    private double latitude;
    private double longitude;
    private String city;
}
