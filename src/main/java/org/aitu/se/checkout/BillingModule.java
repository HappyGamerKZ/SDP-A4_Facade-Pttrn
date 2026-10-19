package org.aitu.se.checkout;

import java.util.UUID;

/**
 * Subsystem C — payment and delivery.
 *
 * <p>Orchestrates the pure {@link PaymentPolicy} rule and the {@link CheckoutRepository} gateway.
 * A declined payment leaves a persisted CANCELLED receipt; a paid receipt can be refunded.
 */
public class BillingModule implements IBillingModule {

    /** Default card limit; charges above it are declined by the bank. */
    public static final double DEFAULT_CARD_LIMIT = 20_000.0;

    private final CheckoutRepository repository;
    private final DeliveryDispatcher deliveryDispatcher;
    private final PaymentPolicy paymentPolicy;

    public BillingModule(String dbUrl) {
        this(dbUrl, new PaymentPolicy(DEFAULT_CARD_LIMIT));
    }

    public BillingModule(String dbUrl, PaymentPolicy paymentPolicy) {
        this.repository = new CheckoutRepository(dbUrl);
        this.deliveryDispatcher = new DeliveryDispatcher();
        this.paymentPolicy = paymentPolicy;
    }

    public BillingModule(CheckoutRepository repository, DeliveryDispatcher deliveryDispatcher, PaymentPolicy paymentPolicy) {
        this.repository = repository;
        this.deliveryDispatcher = deliveryDispatcher;
        this.paymentPolicy = paymentPolicy;
    }

    @Override
    public String processPayment(String ticketId, double amount) {
        Receipt receipt = new Receipt(UUID.randomUUID().toString(), ticketId, amount);
        repository.save(receipt); // persisted as AWAITING_PAYMENT
        System.out.println("[Checkout] Charging " + amount + " for ticket " + ticketId + "...");

        try {
            paymentPolicy.ensureChargeable(amount);
        } catch (PaymentFailedException e) {
            receipt.transitionTo(CheckoutStatus.CANCELLED);
            repository.save(receipt);
            System.out.println("[Checkout] Payment rejected: " + e.getMessage());
            throw e;
        }

        deliveryDispatcher.dispatch(receipt);
        receipt.transitionTo(CheckoutStatus.PAID_AND_DISPATCHED);
        repository.save(receipt);
        System.out.println("[Checkout] Receipt " + receipt.getReceiptId() + " paid and dispatched");
        return receipt.getReceiptId();
    }

    @Override
    public void refund(String receiptId) {
        Receipt receipt = repository.findById(receiptId);
        if (receipt != null && receipt.getStatus() == CheckoutStatus.PAID_AND_DISPATCHED) {
            receipt.transitionTo(CheckoutStatus.REFUNDED);
            repository.save(receipt);
            System.out.println("[Checkout] Receipt " + receiptId + " refunded");
        }
    }

    @Override
    public Receipt findByTicketId(String ticketId) {
        return repository.findByTicketId(ticketId);
    }
}
