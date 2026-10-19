package org.aitu.se.facade;

/**
 * Outcome of a single facade operation: which records were created and in which state the chain
 * ended up. This is what a client sees instead of a bare {@code void}.
 */
public record OrderResult(String orderId,
                          String ticketId,
                          String receiptId,
                          ChainState state,
                          String failureReason) {

    static OrderResult completed(String orderId, String ticketId, String receiptId) {
        return new OrderResult(orderId, ticketId, receiptId, ChainState.COMPLETED, null);
    }

    static OrderResult compensatedAtB(String orderId, String failureReason) {
        return new OrderResult(orderId, null, null, ChainState.COMPENSATED_AT_B, failureReason);
    }

    static OrderResult compensatedAtC(String orderId, String ticketId, String failureReason) {
        return new OrderResult(orderId, ticketId, null, ChainState.COMPENSATED_AT_C, failureReason);
    }

    /** The chain was interrupted after step B and is left on disk in {@code PENDING_C}. */
    static OrderResult interrupted(String orderId, String ticketId) {
        return new OrderResult(orderId, ticketId, null, ChainState.PENDING_C, "Simulated crash after the kitchen step");
    }

    public boolean isCompleted() {
        return state == ChainState.COMPLETED;
    }
}
