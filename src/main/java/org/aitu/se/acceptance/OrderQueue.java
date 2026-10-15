package org.aitu.se.acceptance;

import org.aitu.se.facade.IOrderQueue;

import java.util.Map;
import java.util.UUID;

public class OrderQueue implements IOrderQueue {
    private final IntakeRepository repository;
    private final InventoryChecker inventoryChecker;

    // --------------------------------------------------------
    // КОНСТРУКТОРЫ
    // --------------------------------------------------------

    // Перегруженный конструктор с 1 аргументом (для Unit-тестов и единой БД)
    public OrderQueue(String dbUrl) {
        this(dbUrl, dbUrl);
    }

    // Основной конструктор с 2 аргументами (для раздельных БД)
    public OrderQueue(String intakeDbUrl, String invCheckDbUrl) {
        this.repository = new IntakeRepository(intakeDbUrl);
        this.inventoryChecker = new InventoryChecker(invCheckDbUrl);
    }

    // Конструктор для Pure Unit-тестов с Mock-объектами (опционально)
    public OrderQueue(IntakeRepository repository, InventoryChecker inventoryChecker) {
        this.repository = repository;
        this.inventoryChecker = inventoryChecker;
    }

    // --------------------------------------------------------
    // НОРМАЛЬНЫЙ ПОТОК (Шаг А успешен)
    // --------------------------------------------------------
    @Override
    public String acceptNewOrder(Map<String, Integer> items) {
        String orderId = UUID.randomUUID().toString();
        IntakeRequest request = new IntakeRequest(orderId, items);

        // 1. Проверяем и списываем продукты со склада
        inventoryChecker.reserveItems(items);

        // 2. Если продуктов хватило - переводим статус и сохраняем в БД
        request.transitionTo(IntakeStatus.INVENTORY_RESERVED);
        repository.save(request);

        System.out.println("[Acceptance] Заказ " + orderId + " успешно принят. Статус: " + request.getStatus());
        return orderId;
    }

    // --------------------------------------------------------
    // КОМПЕНСАЦИЯ №1: Если сломалась Кухня (Шаг B)
    // --------------------------------------------------------
    @Override
    public void compensateAndReturnItems(String orderId) {
        IntakeRequest request = repository.findById(orderId);
        if (request != null && request.getStatus() != IntakeStatus.CANCELLED) {
            request.transitionTo(IntakeStatus.CANCELLED);

            // ФИЗИЧЕСКИ ВОЗВРАЩАЕМ продукты на склад
            inventoryChecker.returnItems(request.getItems());

            repository.save(request);
            System.out.println("[Acceptance - Компенсация] Заказ " + orderId + " отменен. Продукты возвращены на склад.");
        }
    }

    // --------------------------------------------------------
    // КОМПЕНСАЦИЯ №2: Если сломалась Оплата (Шаг C)
    // --------------------------------------------------------
    @Override
    public void compensateWithoutReturningItems(String orderId) {
        IntakeRequest request = repository.findById(orderId);
        if (request != null && request.getStatus() != IntakeStatus.CANCELLED) {
            request.transitionTo(IntakeStatus.CANCELLED);

            // Продукты сгорели на кухне, на склад не возвращаем
            repository.save(request);
            System.out.println("[Acceptance - Компенсация] Заказ " + orderId + " отменен. Продукты списаны в убыток (уничтожены).");
        }
    }

    // Вспомогательный метод для получения состояния заказа
    public IntakeRequest getRequest(String orderId) {
        return repository.findById(orderId);
    }
}