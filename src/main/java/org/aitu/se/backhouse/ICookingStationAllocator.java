package org.aitu.se.backhouse;

import java.util.Map;

/**
 * Public API of the Backhouse (kitchen) subsystem (subsystem B).
 *
 * <p>Owned by the subsystem, so subsystem B does not depend on the facade package.
 */
public interface ICookingStationAllocator {

    /**
     * Allocates a free cooking station and prepares the meal.
     *
     * @param orderId correlation id of the upstream order
     * @param items   dishes to cook, used to consult the kitchen stop-list
     * @return the id of the created kitchen ticket
     * @throws NoAvailableStationsException if every station is already busy
     * @throws DishUnavailableException     if a dish is on the stop-list
     */
    String startCooking(String orderId, Map<String, Integer> items);

    /** Marks the ticket as wasted (manual or automatic compensation). */
    void compensateTicket(String ticketId);

    /** Returns the ticket created for the order, or {@code null} if none exists. */
    KitchenTicket findByOrderId(String orderId);
}
