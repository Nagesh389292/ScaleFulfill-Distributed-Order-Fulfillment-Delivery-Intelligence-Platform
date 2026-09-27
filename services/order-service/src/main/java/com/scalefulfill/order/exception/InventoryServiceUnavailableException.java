package com.scalefulfill.order.exception;

public class InventoryServiceUnavailableException extends RuntimeException {
    public InventoryServiceUnavailableException(String message) {
        super(message);
    }

    public InventoryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
