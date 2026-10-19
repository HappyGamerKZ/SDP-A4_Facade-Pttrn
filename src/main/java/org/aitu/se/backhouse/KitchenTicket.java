package org.aitu.se.backhouse;

/**
 * Entity of subsystem B: a kitchen ticket with a strict lifecycle.
 *
 * <p>Allowed transitions:
 * <ul>
 *   <li>{@code PENDING_STATION -> PREPARING} — a station was assigned</li>
 *   <li>{@code PREPARING -> QUALITY_PASSED} — the meal was cooked and checked</li>
 *   <li>{@code * -> WASTED} — scrapped or cancelled from any active state (used for compensation)</li>
 * </ul>
 * {@code WASTED} is terminal.
 */
public class KitchenTicket {
    private final String ticketId;
    private final String orderId; // reference to the order in subsystem A
    private KitchenStatus status;
    private int stationId;

    /** Creates a brand-new ticket; no station is assigned yet. */
    public KitchenTicket(String ticketId, String orderId) {
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.status = KitchenStatus.PENDING_STATION;
        this.stationId = -1;
    }

    /** Used by the repository when reading an existing ticket back from the database. */
    public KitchenTicket(String ticketId, String orderId, KitchenStatus status, int stationId) {
        this.ticketId = ticketId;
        this.orderId = orderId;
        this.status = status;
        this.stationId = stationId;
    }

    public void transitionTo(KitchenStatus newStatus) {
        if (this.status == KitchenStatus.WASTED) {
            throw new IllegalStateException("A wasted or cancelled ticket cannot change its status.");
        }

        if (this.status == KitchenStatus.PENDING_STATION && newStatus == KitchenStatus.PREPARING) {
            this.status = newStatus;
        } else if (this.status == KitchenStatus.PREPARING && newStatus == KitchenStatus.QUALITY_PASSED) {
            this.status = newStatus;
        } else if (newStatus == KitchenStatus.WASTED) {
            this.status = newStatus; // allowed from any active state (rollback)
        } else {
            throw new IllegalStateException("Forbidden kitchen transition: " + this.status + " -> " + newStatus);
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
