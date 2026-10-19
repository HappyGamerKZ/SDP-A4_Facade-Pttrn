package org.aitu.se.facade;

import java.util.Map;

public interface IChainStateManager {
    void updateState(String orderId, String ticketId, ChainState state);

    // Вспомогательный класс-обертка для возврата данных при восстановлении
    class ChainStateRecord {
        public final String orderId;
        public final String ticketId;
        public final ChainState state;

        public ChainStateRecord(String orderId, String ticketId, ChainState state) {
            this.orderId = orderId;
            this.ticketId = ticketId;
            this.state = state;
        }
    }

    Map<String, ChainStateRecord> getIncompleteTransactions();

    // null, если для заказа нет записи
    ChainStateRecord findByOrderId(String orderId);
}