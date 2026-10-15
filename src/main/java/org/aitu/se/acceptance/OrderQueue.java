package org.aitu.se.acceptance;

import org.aitu.se.facade.IOrderQueue;

import java.util.Map;
import java.util.UUID;

public class OrderQueue implements IOrderQueue {
    private final IntakeRepository repository;
    private final InventoryChecker inventoryChecker;

    public OrderQueue(String intake_db_url, String inv_check_db_url) {
        this.repository = new IntakeRepository(intake_db_url);
        this.inventoryChecker = new InventoryChecker(inv_check_db_url);
    }

    // --------------------------------------------------------
    // НОРМАЛЬНЫЙ ПОТОК (Шаг А успешен)
    // --------------------------------------------------------
    @Override
    public String acceptNewOrder(Map<String, Integer> items) {
        // Генерируем уникальный ID для заказа
        String orderId = UUID.randomUUID().toString();
        IntakeRequest request = new IntakeRequest(orderId, items);

        // 1. Проверяем и списываем продукты со склада.
        // Если чего-то нет, метод reserveItems выбросит OutOfStockException
        // и выполнение прервется ДО сохранения в файл! (Требование 3.6)
        inventoryChecker.reserveItems(items);

        // 2. Если продуктов хватило - переводим статус и сохраняем на диск
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
            // Переводим статус в отмененный
            request.transitionTo(IntakeStatus.CANCELLED);

            // ФИЗИЧЕСКИ ВОЗВРАЩАЕМ продукты на склад (плюсуем в файл inventory.txt)
            inventoryChecker.returnItems(request.getItems());

            // Сохраняем обновленный статус заказа
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
            // Переводим статус в отмененный
            request.transitionTo(IntakeStatus.CANCELLED);

            // ВАЖНО: Мы НЕ вызываем inventoryChecker.returnItems().
            // Продукты сгорели на кухне, возвращать нечего.

            repository.save(request);
            System.out.println("[Acceptance - Компенсация] Заказ " + orderId + " отменен. Продукты списаны в убыток (уничтожены).");
        }
    }

    // Вспомогательный метод (пригодится для Фасада админа или тестов)
    public IntakeRequest getRequest(String orderId) {
        return repository.findById(orderId);
    }
}