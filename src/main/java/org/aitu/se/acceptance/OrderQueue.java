package org.aitu.se.acceptance;

import java.util.Map;

/**
 * Subsystem A — order intake and inventory reservation.
 *
 * <p>Orchestrates the pure {@link InventoryChecker} rule and the {@link InventoryRepository}
 * gateway; it does not contain business rules itself.
 */
public class OrderQueue implements IOrderQueue {
    private final IntakeRepository repository;
    private final InventoryRepository inventoryRepository;
    private final InventoryChecker inventoryChecker;

    public OrderQueue(String intakeDbUrl, String inventoryDbUrl) {
        this(new IntakeRepository(intakeDbUrl), new InventoryRepository(inventoryDbUrl), new InventoryChecker());
    }

    /** Convenience constructor for a single shared database file. */
    public OrderQueue(String dbUrl) {
        this(dbUrl, dbUrl);
    }

    public OrderQueue(IntakeRepository repository, InventoryRepository inventoryRepository, InventoryChecker inventoryChecker) {
        this.repository = repository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryChecker = inventoryChecker;
    }

    @Override
    public void acceptNewOrder(String orderId, Map<String, Integer> items) {
        if (repository.findById(orderId) != null) {
            throw new IllegalStateException("Order " + orderId + " already exists");
        }

        IntakeRequest request = new IntakeRequest(orderId, items);
        repository.save(request); // status NEW is persisted as well

        try {
            inventoryChecker.ensureSufficientStock(inventoryRepository.snapshot(), items);
            inventoryRepository.decrement(items);
            request.transitionTo(IntakeStatus.INVENTORY_RESERVED);
            repository.save(request);
        } catch (OutOfStockException e) {
            request.transitionTo(IntakeStatus.CANCELLED);
            repository.save(request);
            throw e;
        }

        System.out.println("[Acceptance] Order " + orderId + " accepted, status " + request.getStatus());
    }

    @Override
    public void cancelAndRestock(String orderId) {
        IntakeRequest request = repository.findById(orderId);
        if (request == null || request.getStatus() == IntakeStatus.CANCELLED) {
            return;
        }
        boolean stockWasReserved = request.getStatus() == IntakeStatus.INVENTORY_RESERVED;
        request.transitionTo(IntakeStatus.CANCELLED);
        // Persist the CANCELLED marker before touching the inventory. The status guard above is
        // what makes this compensation idempotent, so it has to be durable *before* the stock
        // effect; a crash in between can lose one restock, but can never restock twice.
        repository.save(request);
        if (stockWasReserved) {
            inventoryRepository.increment(request.getItems());
        }
        System.out.println("[Acceptance] Order " + orderId + " cancelled, stock returned to the inventory.");
    }

    @Override
    public void cancelWithoutRestock(String orderId) {
        IntakeRequest request = repository.findById(orderId);
        if (request == null || request.getStatus() == IntakeStatus.CANCELLED) {
            return;
        }
        request.transitionTo(IntakeStatus.CANCELLED);
        repository.save(request);
        System.out.println("[Acceptance] Order " + orderId + " cancelled, stock written off as a loss.");
    }

    @Override
    public IntakeRequest getOrder(String orderId) {
        return repository.findById(orderId);
    }

    /** Exposes the inventory gateway so callers can inspect the stock (admin/demo use). */
    public int stockOf(String ingredient) {
        return inventoryRepository.getQuantity(ingredient);
    }
}
