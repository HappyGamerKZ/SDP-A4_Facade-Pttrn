package org.aitu.se.facade;

import org.aitu.se.checkout.PaymentFailedException;
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
                orderQueue.compensateAndReturnItems(record.orderId);
                stateManager.updateState(record.orderId, null, ChainState.COMPENSATED_AT_B);

            } else if (record.state == ChainState.PENDING_C) {
                System.out.println("[Recovery] Двойной откат зависших Шагов B и A...");
                kitchenAllocator.compensateTicket(record.ticketId);
                orderQueue.compensateWithoutReturningItems(record.orderId);
                stateManager.updateState(record.orderId, record.ticketId, ChainState.COMPENSATED_AT_C);
            }
        }
        System.out.println("[Recovery] Восстановление успешно завершено.");
    }

    // ОСНОВНОЙ ПРОЦЕСС
    public void processOrder(Map<String, Integer> items, double amount,
                             boolean forceKitchenFail, boolean forcePaymentFail,
                             boolean simulatePowerLoss) { // Флаг симуляции падения системы

        System.out.println("\n--- [FACADE] ОБРАБОТКА ЗАКАЗА ---");
        String orderId = null;
        String ticketId = null;

        try {
            // ШАГ A
            orderId = orderQueue.acceptNewOrder(items);
            stateManager.updateState(orderId, null, ChainState.PENDING_B);

            // ШАГ B
            ticketId = kitchenAllocator.startCooking(orderId, forceKitchenFail);
            stateManager.updateState(orderId, ticketId, ChainState.PENDING_C);

            // СИМУЛЯЦИЯ ПАДЕНИЯ СИСТЕМЫ (ЛОЖНЫЙ ВЫХОД)
            if (simulatePowerLoss) {
                System.err.println("\n[!!! FATAL !!!] ВНЕЗАПНОЕ ОТКЛЮЧЕНИЕ ПИТАНИЯ СЕРВЕРА!");
                System.err.println("Программа экстренно завершена на этапе PENDING_C. Состояние сохранено в БД.");
                // Имитируем жесткое падение - выходим из метода, не делая откат и не вызывая Шаг С.
                return;
            }

            // ШАГ C
            billingModule.processPayment(ticketId, amount, forcePaymentFail);
            stateManager.updateState(orderId, ticketId, ChainState.COMPLETED);
            System.out.println("--- [FACADE] ЗАКАЗ ЗАВЕРШЕН ---");

        } catch (PaymentFailedException e) {
            System.err.println("[Facade] ОШИБКА ОПЛАТЫ: " + e.getMessage());
            kitchenAllocator.compensateTicket(ticketId);
            orderQueue.compensateWithoutReturningItems(orderId);
            stateManager.updateState(orderId, ticketId, ChainState.COMPENSATED_AT_C);
        } catch (Exception e) { // Ловим ошибки кухни и склада
            // ... логика обычного отката, как мы писали ранее ...
        }
    }
}