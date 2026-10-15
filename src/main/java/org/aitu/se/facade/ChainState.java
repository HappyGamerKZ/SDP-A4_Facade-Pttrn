package org.aitu.se.facade;

public enum ChainState {
    PENDING_B,        // Step A pass, wait Kitchen
    PENDING_C,        // Step B pass, wait Checkout
    COMPLETED,        // All steps pass
    COMPENSATED_AT_B, // Compensated at Kitchen
    COMPENSATED_AT_C  // Compensated at Checkout
}
