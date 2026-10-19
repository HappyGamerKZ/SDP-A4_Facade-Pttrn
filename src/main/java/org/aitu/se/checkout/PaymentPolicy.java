package org.aitu.se.checkout;

/**
 * Pure payment business rules for subsystem C.
 *
 * <p>Holds no state and performs no I/O: it only decides whether a charge is acceptable. This is
 * the "purely business-logic" class of subsystem C, kept separate from {@link CheckoutRepository}
 * (state) and {@link BillingModule} (orchestration).
 */
public class PaymentPolicy {

    private final double cardLimit;

    public PaymentPolicy(double cardLimit) {
        this.cardLimit = cardLimit;
    }

    /**
     * Rejects invalid or unaffordable charges.
     *
     * @throws PaymentFailedException if the amount is not positive or exceeds the card limit
     */
    public void ensureChargeable(double amount) {
        if (amount <= 0) {
            throw new PaymentFailedException("Amount must be positive, but was " + amount);
        }
        if (amount > cardLimit) {
            throw new PaymentFailedException("Bank declined: amount " + amount
                    + " exceeds the card limit of " + cardLimit);
        }
    }

    public double cardLimit() {
        return cardLimit;
    }
}
