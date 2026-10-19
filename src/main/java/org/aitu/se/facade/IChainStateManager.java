package org.aitu.se.facade;

import java.util.Map;

/** Durable store of the facade's chain progress. */
public interface IChainStateManager {

    void updateState(String orderId, String ticketId, ChainState state);

    /** Immutable snapshot of a single chain record. */
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

    /** All transactions that are not finished yet (STARTED, PENDING_B, PENDING_C). */
    Map<String, ChainStateRecord> getIncompleteTransactions();

    /** Returns the record for the order, or {@code null} if there is none. */
    ChainStateRecord findByOrderId(String orderId);
}
