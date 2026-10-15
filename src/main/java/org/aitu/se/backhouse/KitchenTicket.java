package org.aitu.se.backhouse;

public class KitchenTicket {
    private final String ticketId;
    private final String orderId; // Ссылка на заказ из подсистемы А
    private KitchenStatus status;
    private int stationId;

    // Конструктор для нового тикета
    public KitchenTicket(String ticketId, String orderId) {
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.status = KitchenStatus.PENDING_STATION;
        this.stationId = -1; // Станция еще не назначена
    }

    // Конструктор для чтения из БД
    public KitchenTicket(String ticketId, String orderId, KitchenStatus status, int stationId) {
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.status = status;
        this.stationId = stationId;
    }

    public void transitionTo(KitchenStatus newStatus) {
        if (this.status == KitchenStatus.WASTED) {
            throw new IllegalStateException("Нельзя менять статус отбракованного/отмененного тикета.");
        }

        if (this.status == KitchenStatus.PENDING_STATION && newStatus == KitchenStatus.PREPARING) {
            this.status = newStatus;
        } else if (this.status == KitchenStatus.PREPARING && newStatus == KitchenStatus.QUALITY_PASSED) {
            this.status = newStatus;
        } else if (newStatus == KitchenStatus.WASTED) {
            this.status = newStatus; // Переход в WASTED разрешен из любого активного состояния (для отката)
        } else {
            throw new IllegalStateException("Запрещенный переход на кухне: " + this.status + " -> " + newStatus);
        }
    }

    public void setStationId(int stationId) {
        this.stationId = stationId;
    }

    public String getTicketId() { return ticketId; }
    public String getOrderId() { return orderId; }
    public KitchenStatus getStatus() { return status; }
    public int getStationId() { return stationId; }
}