package org.aitu.se.checkout;

public enum CheckoutStatus {
    AWAITING_PAYMENT,    // charge requested, not settled yet
    PAID_AND_DISPATCHED, // charge settled and the courier was dispatched
    CANCELLED,           // charge declined
    REFUNDED             // charge settled, then returned to the customer
}
