package org.aitu.se.acceptance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IntakeEntityTest {

    private Map<String, Integer> items;

    @BeforeEach
    void setUp() {
        items = new HashMap<>();
        items.put("Burger", 2);
    }

    @Test
    @DisplayName("Проверка успешных переходов статуса IntakeRequest")
    void testValidStatusTransitions() {
        IntakeRequest request = new IntakeRequest("ORD-1", items);
        assertEquals(IntakeStatus.NEW, request.getStatus());
        assertEquals("ORD-1", request.getOrderId());
        assertEquals(items, request.getItems());

        request.transitionTo(IntakeStatus.INVENTORY_RESERVED);
        assertEquals(IntakeStatus.INVENTORY_RESERVED, request.getStatus());

        request.transitionTo(IntakeStatus.CANCELLED);
        assertEquals(IntakeStatus.CANCELLED, request.getStatus());
    }

    @Test
    @DisplayName("Проверка запрещенных переходов статуса IntakeRequest")
    void testInvalidStatusTransitions() {
        IntakeRequest request = new IntakeRequest("ORD-1", items);

        // Нельзя из NEW сразу в CANCELLED
        assertThrows(IllegalStateException.class, () -> request.transitionTo(IntakeStatus.CANCELLED));

        request.transitionTo(IntakeStatus.INVENTORY_RESERVED);
        // Нельзя повторно в INVENTORY_RESERVED
        assertThrows(IllegalStateException.class, () -> request.transitionTo(IntakeStatus.INVENTORY_RESERVED));

        request.transitionTo(IntakeStatus.CANCELLED);
        // Из CANCELLED нельзя никуда перейти
        assertThrows(IllegalStateException.class, () -> request.transitionTo(IntakeStatus.INVENTORY_RESERVED));
    }

    @Test
    @DisplayName("Проверка конструктора с полным набором параметров")
    void testFullConstructor() {
        IntakeRequest request = new IntakeRequest("ORD-2", items, IntakeStatus.INVENTORY_RESERVED);
        assertEquals(IntakeStatus.INVENTORY_RESERVED, request.getStatus());
    }

    @Test
    @DisplayName("Проверка исключения OutOfStockException")
    void testOutOfStockException() {
        OutOfStockException ex = new OutOfStockException("Ошибка склада");
        assertEquals("Ошибка склада", ex.getMessage());
    }

    @Test
    @DisplayName("Проверка всех значений Enum IntakeStatus")
    void testEnumValues() {
        for (IntakeStatus status : IntakeStatus.values()) {
            assertNotNull(IntakeStatus.valueOf(status.name()));
        }
    }
}