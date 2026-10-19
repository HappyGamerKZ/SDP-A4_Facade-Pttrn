package org.aitu.se.checkout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for subsystem C: the {@link Receipt} lifecycle and the pure {@link PaymentPolicy} rule.
 */
class CheckoutEntityTest {

    @Test
    @DisplayName("Receipt follows AWAITING_PAYMENT -> PAID_AND_DISPATCHED -> REFUNDED")
    void paidThenRefunded() {
        Receipt receipt = new Receipt("RCP-1", "TCK-1", 5000.0);
        assertEquals(CheckoutStatus.AWAITING_PAYMENT, receipt.getStatus());
        assertEquals("RCP-1", receipt.getReceiptId());
        assertEquals("TCK-1", receipt.getTicketId());
        assertEquals(5000.0, receipt.getAmount());

        receipt.transitionTo(CheckoutStatus.PAID_AND_DISPATCHED);
        assertEquals(CheckoutStatus.PAID_AND_DISPATCHED, receipt.getStatus());

        receipt.transitionTo(CheckoutStatus.REFUNDED);
        assertEquals(CheckoutStatus.REFUNDED, receipt.getStatus());
    }

    @Test
    @DisplayName("A declined receipt is cancelled and cannot be revived")
    void declinedReceipt() {
        Receipt receipt = new Receipt("RCP-2", "TCK-2", 3000.0);

        receipt.transitionTo(CheckoutStatus.CANCELLED);

        assertEquals(CheckoutStatus.CANCELLED, receipt.getStatus());
        assertThrows(IllegalStateException.class, () -> receipt.transitionTo(CheckoutStatus.PAID_AND_DISPATCHED));
    }

    @Test
    @DisplayName("An unpaid receipt cannot be refunded")
    void refundRequiresPayment() {
        Receipt receipt = new Receipt("RCP-3", "TCK-3", 1000.0);
        assertThrows(IllegalStateException.class, () -> receipt.transitionTo(CheckoutStatus.REFUNDED));
    }

    @Test
    @DisplayName("PaymentPolicy rejects non-positive and over-limit charges")
    void paymentPolicy() {
        PaymentPolicy policy = new PaymentPolicy(20_000.0);

        assertDoesNotThrow(() -> policy.ensureChargeable(2500.0));
        assertDoesNotThrow(() -> policy.ensureChargeable(20_000.0));
        assertThrows(PaymentFailedException.class, () -> policy.ensureChargeable(0));
        assertThrows(PaymentFailedException.class, () -> policy.ensureChargeable(-1));
        assertThrows(PaymentFailedException.class, () -> policy.ensureChargeable(20_000.01));
        assertEquals(20_000.0, policy.cardLimit());
    }

    @Test
    @DisplayName("PaymentFailedException carries its message")
    void exceptionMessage() {
        assertEquals("card declined", new PaymentFailedException("card declined").getMessage());
    }
}
