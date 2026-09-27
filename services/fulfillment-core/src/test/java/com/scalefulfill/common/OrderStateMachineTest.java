package com.scalefulfill.common;

import com.scalefulfill.common.exception.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order State Machine Invariant Tests")
class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
    }

    @Test
    @DisplayName("Valid transition: PENDING -> INVENTORY_CHECK -> RESERVED")
    void testValidHappyPathTransitions() {
        assertTrue(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.INVENTORY_CHECK));
        assertTrue(stateMachine.canTransition(OrderStatus.INVENTORY_CHECK, OrderStatus.RESERVED));
        assertTrue(stateMachine.canTransition(OrderStatus.RESERVED, OrderStatus.FULFILLMENT_ASSIGNED));
        assertTrue(stateMachine.canTransition(OrderStatus.FULFILLMENT_ASSIGNED, OrderStatus.PROCESSING));
        assertTrue(stateMachine.canTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED));
        assertTrue(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("Cancellation allowed from PENDING, RESERVED, PROCESSING")
    void testCancellationAllowedBeforeShipped() {
        assertTrue(stateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertTrue(stateMachine.canTransition(OrderStatus.RESERVED, OrderStatus.CANCELLED));
        assertTrue(stateMachine.canTransition(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Cancellation FORBIDDEN once order is SHIPPED or DELIVERED")
    void testCancellationForbiddenOnceShippedOrDelivered() {
        assertFalse(stateMachine.canTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        assertFalse(stateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED));

        assertThrows(InvalidOrderStateException.class, () ->
                stateMachine.validateTransition("ORD-TEST", OrderStatus.SHIPPED, OrderStatus.CANCELLED));

        assertThrows(InvalidOrderStateException.class, () ->
                stateMachine.validateTransition("ORD-TEST", OrderStatus.DELIVERED, OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("Terminal states reject all transitions")
    void testTerminalStatesRejectAllTransitions() {
        assertFalse(stateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PENDING));
        assertFalse(stateMachine.canTransition(OrderStatus.REJECTED, OrderStatus.RESERVED));
        assertFalse(stateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.PROCESSING));
    }
}
