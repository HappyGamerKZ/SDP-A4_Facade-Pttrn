package org.aitu.se.acceptance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for subsystem A, exercised through its own public interface and its own
 * database, without the facade.
 */
class OrderQueueIntegrationTest {

    @TempDir
    Path tempDir;

    private String dbUrl;
    private OrderQueue orderQueue;

    @BeforeEach
    void setUp() {
        dbUrl = "jdbc:sqlite:" + tempDir.resolve("acceptance.db")
                .toAbsolutePath().toString().replace('\\', '/');
        orderQueue = new OrderQueue(dbUrl);
    }

    private static Map<String, Integer> orderOf(String dish, int qty) {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put(dish, qty);
        return items;
    }

    @Test
    @DisplayName("A new order reserves stock and is persisted as INVENTORY_RESERVED")
    void acceptReservesStock() {
        orderQueue.acceptNewOrder("ORD-1", orderOf("Burger", 2));

        assertEquals(IntakeStatus.INVENTORY_RESERVED, orderQueue.getOrder("ORD-1").getStatus());
        assertEquals(98, orderQueue.stockOf("Burger"));
    }

    @Test
    @DisplayName("An unaffordable order is persisted as CANCELLED and leaves the stock intact")
    void acceptOutOfStock() {
        assertThrows(OutOfStockException.class, () -> orderQueue.acceptNewOrder("ORD-2", orderOf("Burger", 9999)));

        assertEquals(IntakeStatus.CANCELLED, orderQueue.getOrder("ORD-2").getStatus());
        assertEquals(100, orderQueue.stockOf("Burger"));
    }

    @Test
    @DisplayName("Compensation with restock returns the reserved ingredients")
    void cancelAndRestock() {
        orderQueue.acceptNewOrder("ORD-3", orderOf("Burger", 2));

        orderQueue.cancelAndRestock("ORD-3");

        assertEquals(IntakeStatus.CANCELLED, orderQueue.getOrder("ORD-3").getStatus());
        assertEquals(100, orderQueue.stockOf("Burger"));
    }

    @Test
    @DisplayName("Compensation without restock writes the ingredients off as a loss")
    void cancelWithoutRestock() {
        orderQueue.acceptNewOrder("ORD-4", orderOf("Burger", 2));

        orderQueue.cancelWithoutRestock("ORD-4");

        assertEquals(IntakeStatus.CANCELLED, orderQueue.getOrder("ORD-4").getStatus());
        assertEquals(98, orderQueue.stockOf("Burger"));
    }

    @Test
    @DisplayName("Restocking compensation is idempotent: a repeated call never restocks twice")
    void cancelAndRestockIsIdempotent() {
        orderQueue.acceptNewOrder("ORD-IDEMP", orderOf("Burger", 2));

        orderQueue.cancelAndRestock("ORD-IDEMP");
        orderQueue.cancelAndRestock("ORD-IDEMP");

        assertEquals(IntakeStatus.CANCELLED, orderQueue.getOrder("ORD-IDEMP").getStatus());
        assertEquals(100, orderQueue.stockOf("Burger"));
    }

    @Test
    @DisplayName("Compensating an unknown order is a harmless no-op")
    void compensateUnknownOrderIsNoOp() {
        assertDoesNotThrow(() -> orderQueue.cancelAndRestock("NO-SUCH"));
        assertDoesNotThrow(() -> orderQueue.cancelWithoutRestock("NO-SUCH"));
    }

    @Test
    @DisplayName("State and stock survive a restart of the subsystem")
    void stateSurvivesRestart() {
        orderQueue.acceptNewOrder("ORD-5", orderOf("Burger", 1));

        OrderQueue restarted = new OrderQueue(dbUrl);

        assertEquals(IntakeStatus.INVENTORY_RESERVED, restarted.getOrder("ORD-5").getStatus());
        assertEquals(99, restarted.stockOf("Burger"));
    }

    @Test
    @DisplayName("Duplicate order ids are rejected")
    void duplicateOrderId() {
        orderQueue.acceptNewOrder("ORD-6", orderOf("Burger", 1));

        assertThrows(IllegalStateException.class, () -> orderQueue.acceptNewOrder("ORD-6", orderOf("Burger", 1)));
    }

    @Test
    @DisplayName("An unknown order is reported as absent")
    void unknownOrder() {
        assertNull(orderQueue.getOrder("NO-SUCH"));
    }
}
