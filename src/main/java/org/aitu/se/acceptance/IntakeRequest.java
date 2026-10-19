package org.aitu.se.acceptance;

import java.util.Map;

/**
 * Entity of subsystem A: an intake request with a strict, persisted lifecycle.
 *
 * <p>Allowed transitions:
 * <ul>
 *   <li>{@code NEW -> INVENTORY_RESERVED} — stock reserved successfully</li>
 *   <li>{@code NEW -> CANCELLED} — rejected before reservation (e.g. out of stock)</li>
 *   <li>{@code INVENTORY_RESERVED -> CANCELLED} — compensated after a downstream failure</li>
 * </ul>
 */
public class IntakeRequest {
    private final String orderId;
    private final Map<String, Integer> items;
    private IntakeStatus status;

    public IntakeRequest(String orderId, Map<String, Integer> items) {
        this(orderId, items, IntakeStatus.NEW);
    }

    /** Used by the repository when reading an existing request back from the database. */
    public IntakeRequest(String orderId, Map<String, Integer> items, IntakeStatus status) {
        this.orderId = orderId;
        this.items = items;
        this.status = status;
    }

    public void transitionTo(IntakeStatus newStatus) {
        if (this.status == IntakeStatus.CANCELLED) {
            throw new IllegalStateException("A cancelled order cannot change its status.");
        }
        boolean allowed =
                (this.status == IntakeStatus.NEW && newStatus == IntakeStatus.INVENTORY_RESERVED)
                        || (this.status == IntakeStatus.NEW && newStatus == IntakeStatus.CANCELLED)
                        || (this.status == IntakeStatus.INVENTORY_RESERVED && newStatus == IntakeStatus.CANCELLED);
        if (!allowed) {
            throw new IllegalStateException("Forbidden status transition: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
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
