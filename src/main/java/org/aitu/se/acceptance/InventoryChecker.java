package org.aitu.se.acceptance;

import java.util.Map;

/**
 * Pure inventory business rules for subsystem A.
 *
 * <p>Holds no state and performs no I/O: it only validates a stock snapshot against a requested
 * order. Persistence is the responsibility of {@link InventoryRepository}. This separation keeps
 * the rule independently testable and free of SQL.
 */
public class InventoryChecker {

    /**
     * Verifies that the requested quantities can be fulfilled from the given stock snapshot.
     *
     * @param stock     current stock, ingredient -> available quantity
     * @param requested requested quantities, ingredient -> quantity
     * @throws OutOfStockException if a quantity is invalid or not enough stock is available
     */
    public void ensureSufficientStock(Map<String, Integer> stock, Map<String, Integer> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new OutOfStockException("The order is empty");
        }
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            int requestedQty = entry.getValue() == null ? 0 : entry.getValue();
            if (requestedQty <= 0) {
                throw new OutOfStockException("Invalid quantity for '" + entry.getKey() + "': " + entry.getValue());
            }
            int available = stock.getOrDefault(entry.getKey(), 0);
            if (available < requestedQty) {
                throw new OutOfStockException("Not enough stock for '" + entry.getKey()
                        + "' (requested " + requestedQty + ", available " + available + ")");
            }
        }
    }
}
