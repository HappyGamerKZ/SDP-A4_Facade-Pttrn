package org.aitu.se.facade;

import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;

// Фасад для ручного вмешательства администратора
public class AdminCompensationFacade {
    private final IOrderQueue orderQueue;
    private final ICookingStationAllocator kitchenAllocator;
    private final IChainStateManager stateManager;

    // 1. Главный конструктор (Dependency Injection) — используется для Unit-тестов и гибкой настройки
    public AdminCompensationFacade(IOrderQueue orderQueue,
                                   ICookingStationAllocator kitchenAllocator,
                                   IChainStateManager stateManager) {
        this.orderQueue = orderQueue;
        this.kitchenAllocator = kitchenAllocator;
        this.stateManager = stateManager;
    }

    // 2. Вспомогательный конструктор — инициализирует подсистемы по URL баз данных (для Main)
    public AdminCompensationFacade(String kitchenDbUrl, String intakeDbUrl, String invCheckDbUrl, String chainStateDbUrl) {
        this(
                new OrderQueue(intakeDbUrl, invCheckDbUrl),
                new CookingStationAllocator(kitchenDbUrl),
                new ChainStateManager(chainStateDbUrl)
        );
    }

    // Ручная принудительная отмена заказа (например, клиент передумал во время готовки)
    public void forceCancelOrder(String orderId, String ticketId) {
        System.out.println("\n[AdminFacade] ВНИМАНИЕ: Запущен ручной откат заказа " + orderId);

        if (ticketId != null) {
            System.out.println("[AdminFacade] Отменяем кухонный тикет...");
            kitchenAllocator.compensateTicket(ticketId);
        }

        System.out.println("[AdminFacade] Отменяем первичную заявку (без возврата продуктов)...");
        orderQueue.compensateWithoutReturningItems(orderId);

        stateManager.updateState(orderId, ticketId, ChainState.COMPENSATED_AT_C);
        System.out.println("[AdminFacade] Ручной откат успешно завершен.");
    }
}