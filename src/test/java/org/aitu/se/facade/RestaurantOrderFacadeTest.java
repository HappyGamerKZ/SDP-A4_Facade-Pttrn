package org.aitu.se.facade;

import org.aitu.se.acceptance.IntakeStatus;
import org.aitu.se.acceptance.InventoryChecker;
import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;
import org.aitu.se.backhouse.KitchenRepository;
import org.aitu.se.backhouse.KitchenStatus;
import org.aitu.se.backhouse.KitchenTicket;
import org.aitu.se.checkout.BillingModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantOrderFacadeTest {

    private RestaurantOrderFacade facade;
    private ChainStateManager stateManager;
    private OrderQueue orderQueue;
    private CookingStationAllocator kitchenAllocator;
    private BillingModule billingModule;
    private InventoryChecker inventory;
    private KitchenRepository kitchenTickets;

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
        inventory = new InventoryChecker(dbUrl);
        kitchenTickets = new KitchenRepository(dbUrl);

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
        int stockBefore = inventory.getQuantity("Burger");

        OrderResult result = facade.processOrder(items, 2500.0, false, false, false);

        assertTrue(result.isCompleted());
        assertNotNull(result.receiptId());
        assertEquals(ChainState.COMPLETED, stateManager.findByOrderId(result.orderId()).state);
        assertEquals(IntakeStatus.INVENTORY_RESERVED, orderQueue.getRequest(result.orderId()).getStatus());
        assertEquals(KitchenStatus.QUALITY_PASSED, kitchenTickets.findById(result.ticketId()).getStatus());
        assertEquals(stockBefore - 1, inventory.getQuantity("Burger"));
    }

    @Test
    @DisplayName("Ошибка на этапе А (Нехватка складских запасов) -> заказ отклонен, ничего не записано")
    void testProcessOrderOutOfStock() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 10000);
        int stockBefore = inventory.getQuantity("Burger");

        assertThrows(OrderRejectedException.class,
                () -> facade.processOrder(items, 2500.0, false, false, false));

        assertEquals(stockBefore, inventory.getQuantity("Burger"));
        assertTrue(stateManager.getIncompleteTransactions().isEmpty());
    }

    @Test
    @DisplayName("Сбой на этапе B (Кухня) -> Компенсация A (Возврат продуктов)")
    void testProcessOrderKitchenFailure() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 1);
        int stockBefore = inventory.getQuantity("Burger");

        OrderResult result = facade.processOrder(items, 2500.0, true, false, false);

        assertEquals(ChainState.COMPENSATED_AT_B, result.state());
        assertNotNull(result.failureReason());
        assertEquals(ChainState.COMPENSATED_AT_B, stateManager.findByOrderId(result.orderId()).state);
        assertEquals(IntakeStatus.CANCELLED, orderQueue.getRequest(result.orderId()).getStatus());
        assertEquals(stockBefore, inventory.getQuantity("Burger"));
        // Частично созданный тикет кухни не остается висеть в активном статусе
        List<KitchenTicket> tickets = kitchenTickets.findByOrderId(result.orderId());
        assertEquals(1, tickets.size());
        assertEquals(KitchenStatus.WASTED, tickets.get(0).getStatus());
    }

    @Test
    @DisplayName("Сбой на этапе C (Оплата) -> Компенсация B, затем A (Списание продуктов)")
    void testProcessOrderPaymentFailure() {
        Map<String, Integer> items = new HashMap<>();
        items.put("Burger", 1);
        int stockBefore = inventory.getQuantity("Burger");

        OrderResult result = facade.processOrder(items, 2500.0, false, true, false);

        assertEquals(ChainState.COMPENSATED_AT_C, result.state());
        assertEquals(ChainState.COMPENSATED_AT_C, stateManager.findByOrderId(result.orderId()).state);
        assertEquals(KitchenStatus.WASTED, kitchenTickets.findById(result.ticketId()).getStatus());
        assertEquals(IntakeStatus.CANCELLED, orderQueue.getRequest(result.orderId()).getStatus());
        // Блюдо уже приготовлено -> продукты на склад не возвращаются
        assertEquals(stockBefore - 1, inventory.getQuantity("Burger"));
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