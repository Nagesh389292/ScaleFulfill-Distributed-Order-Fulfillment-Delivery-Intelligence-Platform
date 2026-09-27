package com.scalefulfill.search.dto;

import com.scalefulfill.search.model.ProductDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchResponse {
    private List<ProductDocument> results;
    private long totalHits;
    private int page;
    private int size;
    private long executionTimeMs;
}
