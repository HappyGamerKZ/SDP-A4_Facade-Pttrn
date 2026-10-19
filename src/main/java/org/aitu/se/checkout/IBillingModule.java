package org.aitu.se.checkout;

/**
 * Public API of the Checkout subsystem (subsystem C).
 *
 * <p>Owned by the subsystem, so subsystem C does not depend on the facade package.
 */
public interface IBillingModule {

    /**
     * Charges the customer and dispatches the delivery.
     *
     * @param ticketId kitchen ticket the payment belongs to
     * @param amount   amount to charge
     * @return the id of the created receipt
     * @throws PaymentFailedException if the amount is invalid or the bank declines it
     */
    String processPayment(String ticketId, double amount);

    /** Refunds an already paid receipt. No-op if the receipt is unknown or not paid. */
    void refund(String receiptId);

    /** Returns the receipt created for the ticket, or {@code null} if none exists. */
    Receipt findByTicketId(String ticketId);
}
