package org.aitu.se.facade;

import org.aitu.se.acceptance.OutOfStockException;
import org.aitu.se.facade.IChainStateManager.ChainStateRecord;
import java.util.Map;

public class RestaurantOrderFacade {
    private final IOrderQueue orderQueue;
    private final ICookingStationAllocator kitchenAllocator;
    private final IBillingModule billingModule;
    private final IChainStateManager stateManager;

    // Dependency Injection: мы передаем готовые объекты снаружи
    public RestaurantOrderFacade(IOrderQueue oq, ICookingStationAllocator k, IBillingModule b, IChainStateManager sm) {
        this.orderQueue = oq;
        this.kitchenAllocator = k;
        this.billingModule = b;
        this.stateManager = sm;
    }

    // МЕХАНИЗМ АВТО-ВОССТАНОВЛЕНИЯ (Вызывается при старте программы)
    public void recoverIncompleteTransactions() {
        System.out.println("\n[Recovery] Сканирование базы данных на наличие зависших транзакций...");
        Map<String, ChainStateRecord> incomplete = stateManager.getIncompleteTransactions();

        if (incomplete.isEmpty()) {
            System.out.println("[Recovery] Все транзакции завершены корректно. Система готова к работе.");
            return;
        }

        for (ChainStateRecord record : incomplete.values()) {
            System.out.println("[Recovery] Обнаружена зависшая транзакция: Заказ " + record.orderId + " в статусе " + record.state);

            if (record.state == ChainState.PENDING_B) {
                System.out.println("[Recovery] Откат зависшего Шага А...");
                compensateAfterKitchenFailure(record.orderId);
            } else if (record.state == ChainState.PENDING_C) {
                System.out.println("[Recovery] Двойной откат зависших Шагов B и A...");
                compensateAfterPaymentFailure(record.orderId, record.ticketId);
            }
        }
        System.out.println("[Recovery] Восстановление успешно завершено.");
    }

    // ОСНОВНОЙ ПРОЦЕСС
    // Бросает OrderRejectedException, если заказ отклонен на шаге A (ничего не закоммичено).
    // Во всех остальных случаях возвращает итоговое состояние цепочки, уже записанное на диск.
    public OrderResult processOrder(Map<String, Integer> items, double amount,
                                    boolean forceKitchenFail, boolean forcePaymentFail,
                                    boolean simulatePowerLoss) { // Флаг симуляции падения системы

        System.out.println("\n--- [FACADE] ОБРАБОТКА ЗАКАЗА ---");

        // ШАГ A
        String orderId;
        try {
            orderId = orderQueue.acceptNewOrder(items);
        } catch (OutOfStockException e) {
            System.err.println("[Facade] ЗАКАЗ ОТКЛОНЕН: " + e.getMessage());
            throw new OrderRejectedException("Заказ отклонен на складе: " + e.getMessage(), e);
        }
        stateManager.updateState(orderId, null, ChainState.PENDING_B);

        // ШАГ B (A уже закоммичен -> при сбое откатываем A)
        String ticketId;
        try {
            ticketId = kitchenAllocator.startCooking(orderId, forceKitchenFail);
        } catch (RuntimeException e) {
            System.err.println("[Facade] ОШИБКА КУХНИ: " + e.getMessage());
            compensateAfterKitchenFailure(orderId);
            return OrderResult.compensatedAtB(orderId, e.getMessage());
        }
        stateManager.updateState(orderId, ticketId, ChainState.PENDING_C);

        // СИМУЛЯЦИЯ ПАДЕНИЯ СИСТЕМЫ (ЛОЖНЫЙ ВЫХОД)
        if (simulatePowerLoss) {
            System.err.println("\n[!!! FATAL !!!] ВНЕЗАПНОЕ ОТКЛЮЧЕНИЕ ПИТАНИЯ СЕРВЕРА!");
            System.err.println("Программа экстренно завершена на этапе PENDING_C. Состояние сохранено в БД.");
            // Имитируем жесткое падение - выходим из метода, не делая откат и не вызывая Шаг С.
            return OrderResult.interrupted(orderId, ticketId);
        }

        // ШАГ C (A и B уже закоммичены -> при сбое откатываем B, затем A)
        String receiptId;
        try {
            receiptId = billingModule.processPayment(ticketId, amount, forcePaymentFail);
        } catch (RuntimeException e) {
            System.err.println("[Facade] ОШИБКА ОПЛАТЫ: " + e.getMessage());
            compensateAfterPaymentFailure(orderId, ticketId);
            return OrderResult.compensatedAtC(orderId, ticketId, e.getMessage());
        }
        stateManager.updateState(orderId, ticketId, ChainState.COMPLETED);
        System.out.println("--- [FACADE] ЗАКАЗ ЗАВЕРШЕН ---");
        return OrderResult.completed(orderId, ticketId, receiptId);
    }

    // Если сама компенсация упадет, статус останется PENDING_* и откат повторится при следующем recovery
    private void compensateAfterKitchenFailure(String orderId) {
        orderQueue.compensateAndReturnItems(orderId);
        stateManager.updateState(orderId, null, ChainState.COMPENSATED_AT_B);
    }

    private void compensateAfterPaymentFailure(String orderId, String ticketId) {
        kitchenAllocator.compensateTicket(ticketId);
        orderQueue.compensateWithoutReturningItems(orderId);
        stateManager.updateState(orderId, ticketId, ChainState.COMPENSATED_AT_C);
    }
}
