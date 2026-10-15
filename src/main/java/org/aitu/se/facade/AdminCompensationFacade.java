package org.aitu.se.facade;

import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;

// Фасад для ручного вмешательства администратора
public class AdminCompensationFacade {
    private final OrderQueue orderQueue;
    private final CookingStationAllocator kitchenAllocator;
    private final ChainStateManager stateManager;

    public AdminCompensationFacade(String kitchen_db_url, String checkout_db_url, String inv_check_db_url, String chain_st_db_link) {
        this.orderQueue = new OrderQueue(checkout_db_url, inv_check_db_url);
        this.kitchenAllocator = new CookingStationAllocator(kitchen_db_url);
        this.stateManager = new ChainStateManager(chain_st_db_link);
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