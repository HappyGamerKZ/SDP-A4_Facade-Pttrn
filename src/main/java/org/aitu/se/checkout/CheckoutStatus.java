package org.aitu.se.checkout;

public enum CheckoutStatus {
    AWAITING_PAYMENT,    // waiting for payment
    PAID_AND_DISPATCHED, // paid and dispatched
    CANCELLED            // cancelled
}