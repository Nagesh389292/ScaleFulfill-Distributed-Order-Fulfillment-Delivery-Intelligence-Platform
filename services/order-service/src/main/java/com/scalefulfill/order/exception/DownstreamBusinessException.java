package com.scalefulfill.order.exception;

import lombok.Getter;

@Getter
public class DownstreamBusinessException extends RuntimeException {
    private final int statusCode;

    public DownstreamBusinessException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}
