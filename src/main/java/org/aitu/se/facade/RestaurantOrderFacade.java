package org.aitu.se.facade;

import org.aitu.se.acceptance.IOrderQueue;
import org.aitu.se.acceptance.IntakeRequest;
import org.aitu.se.acceptance.IntakeStatus;
import org.aitu.se.acceptance.OutOfStockException;
import org.aitu.se.backhouse.ICookingStationAllocator;
import org.aitu.se.backhouse.KitchenStatus;
import org.aitu.se.backhouse.KitchenTicket;
import org.aitu.se.checkout.CheckoutStatus;
import org.aitu.se.checkout.IBillingModule;
import org.aitu.se.checkout.Receipt;
import org.aitu.se.facade.IChainStateManager.ChainStateRecord;

import java.util.Map;
import java.util.UUID;

/**
 * Primary facade of the restaurant.
 *
 * <p>Hides the three subsystems (acceptance, backhouse, checkout) behind a single operation. For
 * every order it drives the chain A -> B -> C, persists the progress after each step and
 * compensates the committed steps if a later step fails. Recovery on startup consults the
 * <em>actual</em> persisted state of every subsystem, so it can either undo or resume the chain.
 *
 * <h2>Principle of Least Knowledge</h2>
 * A client of this facade only knows {@link #processOrder(Map, double)}. It never touches the
 * subsystems, their interfaces, their exceptions or their databases.
 */
public class RestaurantOrderFacade {

    private final IOrderQueue orderQueue;
    private final ICookingStationAllocator kitchenAllocator;
    private final IBillingModule billingModule;
    private final IChainStateManager stateManager;

    public RestaurantOrderFacade(IOrderQueue orderQueue,
                                 ICookingStationAllocator kitchenAllocator,
                                 IBillingModule billingModule,
                                 IChainStateManager stateManager) {
        this.orderQueue = orderQueue;
        this.kitchenAllocator = kitchenAllocator;
        this.billingModule = billingModule;
        this.stateManager = stateManager;
    }

    // ------------------------------------------------------------------
    // MAIN FLOW
    // ------------------------------------------------------------------

    /**
     * Runs the whole order chain once.
     *
     * @return the persisted end state of the chain (completed, compensated at B, or compensated at C)
     * @throws OrderRejectedException if step A rejects the order (nothing was committed, nothing to undo)
     */
    public OrderResult processOrder(Map<String, Integer> items, double amount) {
        String orderId = UUID.randomUUID().toString();
        System.out.println("\n[Facade] ===== Processing order " + orderId + " =====");

        startChain(orderId);
        runStepA(orderId, items);
        stateManager.updateState(orderId, null, ChainState.PENDING_B);

        // Step B: A is committed, so a B failure must undo A.
        String ticketId;
        try {
            ticketId = kitchenAllocator.startCooking(orderId, items);
        } catch (RuntimeException e) {
            System.err.println("[Facade] Step B (kitchen) failed: " + e.getMessage());
            compensateAfterKitchenFailure(orderId);
            return OrderResult.compensatedAtB(orderId, e.getMessage());
        }
        stateManager.updateState(orderId, ticketId, ChainState.PENDING_C);

        // Step C: A and B are committed, so a C failure must undo B and then A.
        String receiptId;
        try {
            receiptId = billingModule.processPayment(ticketId, amount);
        } catch (RuntimeException e) {
            System.err.println("[Facade] Step C (checkout) failed: " + e.getMessage());
            compensateAfterPaymentFailure(orderId, ticketId);
            return OrderResult.compensatedAtC(orderId, ticketId, e.getMessage());
        }

        stateManager.updateState(orderId, ticketId, ChainState.COMPLETED);
        System.out.println("[Facade] Order " + orderId + " completed");
        return OrderResult.completed(orderId, ticketId, receiptId);
    }

    /**
     * Simulation entry point: runs steps A and B, persists {@code PENDING_C} and returns without
     * running step C, as if the process had died right after the kitchen finished.
     *
     * <p>Used by the demo and by the crash-recovery tests; it is not part of the normal flow.
     */
    public OrderResult simulateCrashAfterKitchen(Map<String, Integer> items, double amount) {
        String orderId = UUID.randomUUID().toString();
        System.out.println("\n[Facade] ===== Simulating a crash after step B for order " + orderId + " =====");

        startChain(orderId);
        runStepA(orderId, items);
        stateManager.updateState(orderId, null, ChainState.PENDING_B);

        String ticketId;
        try {
            ticketId = kitchenAllocator.startCooking(orderId, items);
        } catch (RuntimeException e) {
            compensateAfterKitchenFailure(orderId);
            return OrderResult.compensatedAtB(orderId, e.getMessage());
        }
        stateManager.updateState(orderId, ticketId, ChainState.PENDING_C);

        System.err.println("[Facade] !! PROCESS ABORTED before step C. Chain persisted as PENDING_C.");
        return OrderResult.interrupted(orderId, ticketId);
    }

    // ------------------------------------------------------------------
    // RECOVERY
    // ------------------------------------------------------------------

    /** Called on every startup: finishes or undoes every chain that was left half-way. */
    public void recoverIncompleteTransactions() {
        System.out.println("\n[Recovery] Scanning the persisted chain state for interrupted transactions...");
        Map<String, ChainStateRecord> incomplete = stateManager.getIncompleteTransactions();
        if (incomplete.isEmpty()) {
            System.out.println("[Recovery] Nothing to recover.");
            return;
        }

        for (ChainStateRecord record : incomplete.values()) {
            System.out.println("[Recovery] Order " + record.orderId + " was left in " + record.state);
            switch (record.state) {
                case STARTED -> recoverStarted(record);
                case PENDING_B -> recoverPendingB(record);
                case PENDING_C -> recoverPendingC(record);
                default -> System.out.println("[Recovery] No action required for state " + record.state);
            }
        }
        System.out.println("[Recovery] Done.");
    }

    /** Interrupted before step A confirmed: undo A only if it actually committed the reservation. */
    private void recoverStarted(ChainStateRecord record) {
        IntakeRequest request = orderQueue.getOrder(record.orderId);
        if (request == null) {
            // Step A never created anything, so there is nothing to undo.
            stateManager.updateState(record.orderId, null, ChainState.REJECTED);
        } else if (request.getStatus() == IntakeStatus.INVENTORY_RESERVED) {
            orderQueue.cancelAndRestock(record.orderId);
            stateManager.updateState(record.orderId, null, ChainState.COMPENSATED_AT_A);
        } else if (request.getStatus() == IntakeStatus.CANCELLED) {
            // A previous recovery pass already unwound step A but did not manage to persist its
            // outcome; keep the same terminal state instead of mislabelling it as REJECTED.
            stateManager.updateState(record.orderId, null, ChainState.COMPENSATED_AT_A);
        } else {
            stateManager.updateState(record.orderId, null, ChainState.REJECTED);
        }
    }

    /** Interrupted before step B confirmed: check what the kitchen actually produced. */
    private void recoverPendingB(ChainStateRecord record) {
        KitchenTicket ticket = kitchenAllocator.findByOrderId(record.orderId);
        boolean mealPrepared = ticket != null && ticket.getStatus() == KitchenStatus.QUALITY_PASSED;
        if (ticket != null && ticket.getStatus() != KitchenStatus.WASTED) {
            kitchenAllocator.compensateTicket(ticket.getTicketId());
        }
        // If the meal was already prepared the ingredients are consumed, so do not return them.
        if (mealPrepared) {
            orderQueue.cancelWithoutRestock(record.orderId);
        } else {
            orderQueue.cancelAndRestock(record.orderId);
        }
        stateManager.updateState(record.orderId, ticket == null ? null : ticket.getTicketId(), ChainState.COMPENSATED_AT_B);
    }

    /** Interrupted before step C confirmed: resume if the customer already paid, otherwise unwind. */
    private void recoverPendingC(ChainStateRecord record) {
        Receipt receipt = record.ticketId == null ? null : billingModule.findByTicketId(record.ticketId);
        if (receipt != null && receipt.getStatus() == CheckoutStatus.PAID_AND_DISPATCHED) {
            System.out.println("[Recovery] Payment was already captured -> resuming the chain to COMPLETED");
            stateManager.updateState(record.orderId, record.ticketId, ChainState.COMPLETED);
            return;
        }
        compensateAfterPaymentFailure(record.orderId, record.ticketId);
    }

    // ------------------------------------------------------------------
    // COMPENSATIONS
    // ------------------------------------------------------------------

    private void startChain(String orderId) {
        // Written before step A: closes the window in which stock could leak without a trace.
        stateManager.updateState(orderId, null, ChainState.STARTED);
    }

    private void runStepA(String orderId, Map<String, Integer> items) {
        try {
            orderQueue.acceptNewOrder(orderId, items);
        } catch (OutOfStockException e) {
            stateManager.updateState(orderId, null, ChainState.REJECTED);
            throw new OrderRejectedException(orderId, "Order rejected at inventory: " + e.getMessage(), e);
        }
    }

    /** Undo for a failed step B: the kitchen produced nothing, so return the ingredients. */
    private void compensateAfterKitchenFailure(String orderId) {
        orderQueue.cancelAndRestock(orderId);
        stateManager.updateState(orderId, null, ChainState.COMPENSATED_AT_B);
    }

    /** Undo for a failed step C: scrap the meal (B) and cancel the order without restocking (A). */
    private void compensateAfterPaymentFailure(String orderId, String ticketId) {
        if (ticketId != null) {
            kitchenAllocator.compensateTicket(ticketId);
        }
        orderQueue.cancelWithoutRestock(orderId);
        stateManager.updateState(orderId, ticketId, ChainState.COMPENSATED_AT_C);
    }
}
