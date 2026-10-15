package org.aitu.se.facade;

import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AdminCompensationFacadeTest {

    private AdminCompensationFacade adminFacade;

    @BeforeEach
    void setUp() {
        String dbUrl = "jdbc:sqlite::memory:";
        OrderQueue queue = new OrderQueue(dbUrl, dbUrl);
        CookingStationAllocator allocator = new CookingStationAllocator(dbUrl);
        ChainStateManager manager = new ChainStateManager(dbUrl);

        adminFacade = new AdminCompensationFacade(queue, allocator, manager);
    }

    @Test
    @DisplayName("Ручная принудительная отмена заказа администратором (с тикетом)")
    void testForceCancelWithTicket() {
        assertDoesNotThrow(() -> adminFacade.forceCancelOrder("ORD-ADMIN-1", "TCK-ADMIN-1"));
    }

    @Test
    @DisplayName("Ручная принудительная отмена заказа без кухонного тикета")
    void testForceCancelWithoutTicket() {
        assertDoesNotThrow(() -> adminFacade.forceCancelOrder("ORD-ADMIN-2", null));
    }
}