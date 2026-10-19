package org.aitu.se.facade;

/**
 * Thrown when step A rejects an order (for example, because the inventory cannot satisfy it).
 *
 * <p>Nothing downstream was committed, so there is nothing to compensate. The exception carries
 * the correlation id so the client can still inspect the persisted {@code REJECTED} chain record.
 */
public class OrderRejectedException extends RuntimeException {

    private final String orderId;

    public OrderRejectedException(String orderId, String message, Throwable cause) {
        super(message, cause);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
