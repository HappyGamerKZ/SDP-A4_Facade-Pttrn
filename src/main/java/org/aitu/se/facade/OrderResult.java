package org.aitu.se.facade;

// Итог одной операции фасада для клиента: какие записи созданы и в каком состоянии осталась цепочка
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

    // Цепочка прервана "сбоем питания" и осталась на диске в PENDING_C до восстановления
    static OrderResult interrupted(String orderId, String ticketId) {
        return new OrderResult(orderId, ticketId, null, ChainState.PENDING_C, "Simulated power loss");
    }

    public boolean isCompleted() {
        return state == ChainState.COMPLETED;
    }
}
