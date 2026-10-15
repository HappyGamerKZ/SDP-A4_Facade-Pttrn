package org.aitu.se.acceptance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderQueueIntegrationTest {

    private OrderQueue orderQueue;
    private final String dbUrl = "jdbc:sqlite::memory:";

    @TempDir
    Path tempDir;

    private String testDbUrl() {
        return "jdbc:sqlite:" + tempDir.resolve("test.db")
                .toAbsolutePath()
                .toString()
                .replace('\\', '/');
    }

    @BeforeEach
    void setUp() {
        String dbUrl = testDbUrl();
        orderQueue = new OrderQueue(dbUrl, dbUrl);
    }
    @Test
    @DisplayName("Прием нового заказа и успешное списание со склада")
    void testAcceptNewOrderSuccess() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 2);

        String orderId = orderQueue.acceptNewOrder(items);
        assertNotNull(orderId);

        IntakeRequest request = orderQueue.getRequest(orderId);
        assertNotNull(request);
        assertEquals(IntakeStatus.INVENTORY_RESERVED, request.getStatus());
    }

    @Test
    @DisplayName("Отказ при недостаточном количестве продуктов на складе")
    void testAcceptNewOrderOutOfStock() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 9999); // Больше, чем есть на складе

        assertThrows(OutOfStockException.class, () -> orderQueue.acceptNewOrder(items));
    }

    @Test
    @DisplayName("Компенсация с возвратом ингредиентов на склад")
    void testCompensateAndReturnItems() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 2);

        String orderId = orderQueue.acceptNewOrder(items);
        orderQueue.compensateAndReturnItems(orderId);

        IntakeRequest request = orderQueue.getRequest(orderId);
        assertEquals(IntakeStatus.CANCELLED, request.getStatus());
    }

    @Test
    @DisplayName("Компенсация без возврата ингредиентов (продукты уничтожены)")
    void testCompensateWithoutReturningItems() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 2);

        String orderId = orderQueue.acceptNewOrder(items);
        orderQueue.compensateWithoutReturningItems(orderId);

        IntakeRequest request = orderQueue.getRequest(orderId);
        assertEquals(IntakeStatus.CANCELLED, request.getStatus());
    }
}