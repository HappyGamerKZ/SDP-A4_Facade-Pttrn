package org.aitu.se.acceptance;

import java.util.Map;

public class IntakeRequest {
    final private String orderId;
    private IntakeStatus status; // Используем наш enum вместо String
    private final Map<String, Integer> items;

    public IntakeRequest(String orderId, Map<String, Integer> items) {
        this.orderId = orderId;
        this.items = items;
        this.status = IntakeStatus.NEW; // Присваиваем одно из строгих значений
    }

    // Используется в IntakeRepository при ЧТЕНИИ из базы данных
    public IntakeRequest(String orderId, Map<String, Integer> items, IntakeStatus status) {
        this.orderId = orderId;
        this.items = items;
        this.status = status;
    }

    public void transitionTo(IntakeStatus newStatus) {
        if (this.status == IntakeStatus.CANCELLED) {
            throw new IllegalStateException("Нельзя изменить статус отмененного заказа.");
        }

        if (this.status == IntakeStatus.NEW && newStatus == IntakeStatus.INVENTORY_RESERVED) {
            this.status = newStatus;
        } else if (this.status == IntakeStatus.INVENTORY_RESERVED && newStatus == IntakeStatus.CANCELLED) {
            this.status = newStatus; // Сценарий отката (компенсации) от Фасада
        } else {
            throw new IllegalStateException("Запрещенный переход статуса: " + this.status + " -> " + newStatus);
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public Map<String, Integer> getItems() {
        return items;
    }

    public IntakeStatus getStatus() {
        return status;
    }
}