package org.aitu.se.facade;

import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;
import org.aitu.se.checkout.BillingModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantOrderFacadeTest {

    private RestaurantOrderFacade facade;
    private ChainStateManager stateManager;
    private OrderQueue orderQueue;
    private CookingStationAllocator kitchenAllocator;
    private BillingModule billingModule;

    private String dbUrl;

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
        dbUrl = testDbUrl();

        orderQueue = new OrderQueue(dbUrl);
        kitchenAllocator = new CookingStationAllocator(dbUrl);
        billingModule = new BillingModule(dbUrl);
        stateManager = new ChainStateManager(dbUrl);

        facade = new RestaurantOrderFacade(
                orderQueue, kitchenAllocator,
                billingModule, stateManager
        );
    }

    @Test
    @DisplayName("Успешное выполнение заказа (Happy Path)")
    void testProcessOrderSuccess() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 1);

        assertDoesNotThrow(() -> facade.processOrder(items, 2500.0, false, false, false));
    }

    @Test
    @DisplayName("Ошибка на этапе А (Нехватка складских запасов)")
    void testProcessOrderOutOfStock() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 10000);

        assertDoesNotThrow(() -> facade.processOrder(items, 2500.0, false, false, false));
    }

    @Test
    @DisplayName("Сбой на этапе B (Кухня) -> Компенсация B -> A (Возврат продуктов)")
    void testProcessOrderKitchenFailure() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 1);

        assertDoesNotThrow(() -> facade.processOrder(items, 2500.0, true, false, false));
    }

    @Test
    @DisplayName("Сбой на этапе C (Оплата) -> Компенсация C -> B -> A (Списание продуктов)")
    void testProcessOrderPaymentFailure() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 1);

        assertDoesNotThrow(() -> facade.processOrder(items, 2500.0, false, true, false));
    }

    @Test
    @DisplayName("Имитация внезапного сбоя питания (Power Loss Simulation)")
    void testProcessOrderPowerLoss() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 1);

        // Симуляция завершает выполнение до оплаты
        facade.processOrder(items, 2500.0, false, false, true);

        // Проверяем, что в базе зависла транзакция PENDING_C
        Map<String, IChainStateManager.ChainStateRecord> incomplete = stateManager.getIncompleteTransactions();
        assertEquals(1, incomplete.size());
    }

    @Test
    @DisplayName("Авто-восстановление зависшей транзакции PENDING_C после перезапуска")
    void testRecoveryPendingC() {
        // 1. Создаем незавершенную транзакцию
        stateManager.updateState("ORD-CRASH-1", "TCK-CRASH-1", ChainState.PENDING_C);

        // 2. Запускаем восстановление
        facade.recoverIncompleteTransactions();

        // 3. Проверяем, что зависших транзакций не осталось
        Map<String, IChainStateManager.ChainStateRecord> incomplete = stateManager.getIncompleteTransactions();
        assertTrue(incomplete.isEmpty());
    }

    @Test
    @DisplayName("Авто-восстановление зависшей транзакции PENDING_B после перезапуска")
    void testRecoveryPendingB() {
        stateManager.updateState("ORD-CRASH-2", null, ChainState.PENDING_B);

        facade.recoverIncompleteTransactions();

        Map<String, IChainStateManager.ChainStateRecord> incomplete = stateManager.getIncompleteTransactions();
        assertTrue(incomplete.isEmpty());
    }

    @Test
    @DisplayName("Восстановление при отсутствии зависших транзакций")
    void testRecoveryEmpty() {
        assertDoesNotThrow(() -> facade.recoverIncompleteTransactions());
    }
}