package com.scalefulfill.inventory.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    
    private int status;
    private String error;
    private String message;
    private String path;
    private List<String> validationErrors;
}
