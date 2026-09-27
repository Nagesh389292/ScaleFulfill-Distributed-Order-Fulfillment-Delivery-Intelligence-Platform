package com.scalefulfill.search.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDocument {

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("sku")
    private String sku;

    @JsonProperty("name")
    private String name;

    @JsonProperty("price")
    private BigDecimal price;

    @JsonProperty("weight_kg")
    private BigDecimal weightKg;

    @JsonProperty("available_quantity")
    private Integer availableQuantity;

    @JsonProperty("fulfillment_centers")
    private List<String> fulfillmentCenters;

    @JsonProperty("search_text")
    private String searchText;
}
