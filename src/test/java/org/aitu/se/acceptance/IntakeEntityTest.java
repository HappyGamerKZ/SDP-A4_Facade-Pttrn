package org.aitu.se.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for subsystem A: the {@link IntakeRequest} lifecycle and the pure
 * {@link InventoryChecker} rule.
 */
class IntakeEntityTest {

    private static Map<String, Integer> orderOf(String dish, int qty) {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put(dish, qty);
        return items;
    }

    @Test
    @DisplayName("IntakeRequest follows NEW -> INVENTORY_RESERVED -> CANCELLED")
    void validLifecycle() {
        IntakeRequest request = new IntakeRequest("ORD-1", orderOf("Burger", 2));
        assertEquals(IntakeStatus.NEW, request.getStatus());
        assertEquals("ORD-1", request.getOrderId());
        assertEquals(orderOf("Burger", 2), request.getItems());

        request.transitionTo(IntakeStatus.INVENTORY_RESERVED);
        assertEquals(IntakeStatus.INVENTORY_RESERVED, request.getStatus());

        request.transitionTo(IntakeStatus.CANCELLED);
        assertEquals(IntakeStatus.CANCELLED, request.getStatus());
    }

    @Test
    @DisplayName("An order rejected before reservation goes straight to CANCELLED")
    void rejectedBeforeReservation() {
        IntakeRequest request = new IntakeRequest("ORD-2", orderOf("Burger", 1));

        request.transitionTo(IntakeStatus.CANCELLED);

        assertEquals(IntakeStatus.CANCELLED, request.getStatus());
    }

    @Test
    @DisplayName("Forbidden transitions are rejected and CANCELLED is final")
    void forbiddenTransitions() {
        IntakeRequest request = new IntakeRequest("ORD-3", orderOf("Burger", 1));
        request.transitionTo(IntakeStatus.INVENTORY_RESERVED);

        assertThrows(IllegalStateException.class, () -> request.transitionTo(IntakeStatus.INVENTORY_RESERVED));
        assertThrows(IllegalStateException.class, () -> request.transitionTo(IntakeStatus.NEW));

        request.transitionTo(IntakeStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> request.transitionTo(IntakeStatus.INVENTORY_RESERVED));
    }

    @Test
    @DisplayName("The full constructor restores a persisted status")
    void fullConstructor() {
        IntakeRequest request = new IntakeRequest("ORD-4", orderOf("Burger", 1), IntakeStatus.INVENTORY_RESERVED);
        assertEquals(IntakeStatus.INVENTORY_RESERVED, request.getStatus());
    }

    @Test
    @DisplayName("InventoryChecker accepts a satisfiable order and rejects the rest")
    void inventoryChecker() {
        InventoryChecker checker = new InventoryChecker();
        Map<String, Integer> stock = orderOf("Burger", 5);

        assertDoesNotThrow(() -> checker.ensureSufficientStock(stock, orderOf("Burger", 5)));
        assertThrows(OutOfStockException.class, () -> checker.ensureSufficientStock(stock, orderOf("Burger", 6)));
        assertThrows(OutOfStockException.class, () -> checker.ensureSufficientStock(stock, orderOf("Cola", 1)));
        assertThrows(OutOfStockException.class, () -> checker.ensureSufficientStock(stock, orderOf("Burger", 0)));
        assertThrows(OutOfStockException.class, () -> checker.ensureSufficientStock(stock, new LinkedHashMap<>()));
    }

    @Test
    @DisplayName("OutOfStockException carries its message")
    void exceptionMessage() {
        assertEquals("out of stock", new OutOfStockException("out of stock").getMessage());
    }
}
