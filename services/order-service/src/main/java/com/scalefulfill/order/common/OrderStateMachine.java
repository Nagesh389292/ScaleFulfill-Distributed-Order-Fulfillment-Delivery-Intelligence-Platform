package com.scalefulfill.order.common;

import com.scalefulfill.order.exception.InvalidOrderStateException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        VALID_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.INVENTORY_CHECK, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.INVENTORY_CHECK, EnumSet.of(OrderStatus.RESERVED, OrderStatus.PENDING_INVENTORY_VERIFICATION, OrderStatus.REJECTED));
        VALID_TRANSITIONS.put(OrderStatus.PENDING_INVENTORY_VERIFICATION, EnumSet.of(OrderStatus.RESERVED, OrderStatus.REJECTED, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.RESERVED, EnumSet.of(OrderStatus.FULFILLMENT_ASSIGNED, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.FULFILLMENT_ASSIGNED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        VALID_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        VALID_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        VALID_TRANSITIONS.put(OrderStatus.REJECTED, EnumSet.noneOf(OrderStatus.class));
        VALID_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) return false;
        Set<OrderStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public void validateTransition(String orderId, OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidOrderStateException(
                    String.format("Invalid order state transition for order [%s] from [%s] to [%s]", orderId, from, to)
            );
        }
    }
}
