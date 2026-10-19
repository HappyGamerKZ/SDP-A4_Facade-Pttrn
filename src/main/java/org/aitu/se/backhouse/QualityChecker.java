package org.aitu.se.backhouse;

/**
 * Pure quality rules for subsystem B.
 *
 * <p>Holds no state and performs no I/O: it validates the invariants of a ticket that is about to
 * be finished. It is the "purely business-logic" class of subsystem B, kept separate from
 * {@link KitchenRepository} (state) and {@link CookingStationAllocator} (orchestration).
 */
public class QualityChecker {

    /**
     * Verifies that a ticket is in a state from which cooking can be completed.
     *
     * @throws QualityCheckFailedException if the ticket is not being prepared or has no station
     */
    public void verifyMeal(KitchenTicket ticket) {
        if (ticket == null) {
            throw new QualityCheckFailedException("No ticket to verify");
        }
        if (ticket.getStatus() != KitchenStatus.PREPARING) {
            throw new QualityCheckFailedException("Ticket " + ticket.getTicketId()
                    + " is not being prepared (status " + ticket.getStatus() + ")");
        }
        if (ticket.getStationId() < 1 || ticket.getStationId() > CookingStationAllocator.MAX_STATIONS) {
            throw new QualityCheckFailedException("Ticket " + ticket.getTicketId()
                    + " has an invalid station id: " + ticket.getStationId());
        }
    }
}
