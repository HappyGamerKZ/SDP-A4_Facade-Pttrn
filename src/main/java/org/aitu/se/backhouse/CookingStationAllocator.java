package org.aitu.se.backhouse;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Subsystem B — cooking station allocation.
 *
 * <p>Failures are driven by real, persisted conditions instead of test flags: a station is
 * allocated only if a station is free (occupancy is read from the database) and no requested dish
 * is on the kitchen stop-list. Validation happens before any ticket is written, so a rejected order
 * never leaves an orphan ticket behind.
 */
public class CookingStationAllocator implements ICookingStationAllocator {

    public static final int MAX_STATIONS = 3;

    private final KitchenRepository repository;
    private final QualityChecker qualityChecker;

    public CookingStationAllocator(String dbUrl) {
        this.repository = new KitchenRepository(dbUrl);
        this.qualityChecker = new QualityChecker();
    }

    public CookingStationAllocator(KitchenRepository repository, QualityChecker qualityChecker) {
        this.repository = repository;
        this.qualityChecker = qualityChecker;
    }

    @Override
    public String startCooking(String orderId, Map<String, Integer> items) {
        if (repository.countByStatus(KitchenStatus.PREPARING) >= MAX_STATIONS) {
            throw new NoAvailableStationsException("All " + MAX_STATIONS + " cooking stations are busy for order " + orderId);
        }
        if (items != null) {
            for (String dish : items.keySet()) {
                if (repository.isOnStopList(dish)) {
                    throw new DishUnavailableException("Dish '" + dish + "' is on the kitchen stop-list");
                }
            }
        }

        String ticketId = UUID.randomUUID().toString();
        KitchenTicket ticket = new KitchenTicket(ticketId, orderId);
        ticket.setStationId(nextFreeStation());
        ticket.transitionTo(KitchenStatus.PREPARING);
        repository.save(ticket);
        System.out.println("[Backhouse] Cooking order " + orderId + " on station #" + ticket.getStationId());

        try {
            qualityChecker.verifyMeal(ticket);
            ticket.transitionTo(KitchenStatus.QUALITY_PASSED);
            repository.save(ticket);
            System.out.println("[Backhouse] Ticket " + ticketId + " passed quality control");
            return ticketId;
        } catch (RuntimeException e) {
            ticket.transitionTo(KitchenStatus.WASTED);
            repository.save(ticket);
            System.out.println("[Backhouse] Ticket " + ticketId + " marked as WASTED");
            throw e;
        }
    }

    @Override
    public void compensateTicket(String ticketId) {
        KitchenTicket ticket = repository.findById(ticketId);
        if (ticket != null && ticket.getStatus() != KitchenStatus.WASTED) {
            ticket.transitionTo(KitchenStatus.WASTED);
            repository.save(ticket);
            System.out.println("[Backhouse] Ticket " + ticketId + " marked as WASTED");
        }
    }

    @Override
    public KitchenTicket findByOrderId(String orderId) {
        return repository.findByOrderId(orderId);
    }

    private int nextFreeStation() {
        Set<Integer> occupied = new HashSet<>(repository.occupiedStations());
        for (int station = 1; station <= MAX_STATIONS; station++) {
            if (!occupied.contains(station)) {
                return station;
            }
        }
        throw new NoAvailableStationsException("No free cooking station available");
    }
}
