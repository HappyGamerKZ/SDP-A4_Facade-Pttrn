package org.aitu.se.checkout;

/**
 * Entity of subsystem C: a receipt with a strict, persisted lifecycle.
 *
 * <p>Allowed transitions:
 * <ul>
 *   <li>{@code AWAITING_PAYMENT -> PAID_AND_DISPATCHED} — charge accepted</li>
 *   <li>{@code AWAITING_PAYMENT -> CANCELLED} — charge declined</li>
 *   <li>{@code PAID_AND_DISPATCHED -> REFUNDED} — money returned after a cancellation</li>
 * </ul>
 */
public class Receipt {
    private final String receiptId;
    private final String ticketId;
    private final double amount;
    private CheckoutStatus status;

    public Receipt(String receiptId, String ticketId, double amount) {
        this(receiptId, ticketId, amount, CheckoutStatus.AWAITING_PAYMENT);
    }

    /** Used by the repository when reading an existing receipt back from the database. */
    public Receipt(String receiptId, String ticketId, double amount, CheckoutStatus status) {
        this.receiptId = receiptId;
        this.ticketId = ticketId;
        this.amount = amount;
        this.status = status;
    }

    public void transitionTo(CheckoutStatus newStatus) {
        boolean allowed =
                (this.status == CheckoutStatus.AWAITING_PAYMENT && newStatus == CheckoutStatus.PAID_AND_DISPATCHED)
                        || (this.status == CheckoutStatus.AWAITING_PAYMENT && newStatus == CheckoutStatus.CANCELLED)
                        || (this.status == CheckoutStatus.PAID_AND_DISPATCHED && newStatus == CheckoutStatus.REFUNDED);
        if (!allowed) {
            throw new IllegalStateException("Forbidden receipt transition: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public double getAmount() {
        return amount;
    }

    public CheckoutStatus getStatus() {
        return status;
    }
}
