package org.aitu.se.facade;

import org.aitu.se.acceptance.IOrderQueue;
import org.aitu.se.acceptance.IntakeRequest;
import org.aitu.se.backhouse.ICookingStationAllocator;
import org.aitu.se.backhouse.KitchenStatus;
import org.aitu.se.backhouse.KitchenTicket;
import org.aitu.se.checkout.CheckoutStatus;
import org.aitu.se.checkout.IBillingModule;
import org.aitu.se.checkout.Receipt;

import java.util.Map;

/**
 * Secondary facade for administrative operations.
 *
 * <p>Provides inspection of the persisted state and a state-aware manual cancellation. Unlike a
 * blind "cancel", {@link #forceCancelOrder(String)} reads the current chain state, refuses to
 * touch unknown or already-compensated orders and refunds the customer when the order was paid.
 */
public class AdminCompensationFacade {

    private final IOrderQueue orderQueue;
    private final ICookingStationAllocator kitchenAllocator;
    private final IBillingModule billingModule;
    private final IChainStateManager stateManager;

    public AdminCompensationFacade(IOrderQueue orderQueue,
                                   ICookingStationAllocator kitchenAllocator,
                                   IBillingModule billingModule,
                                   IChainStateManager stateManager) {
        this.orderQueue = orderQueue;
        this.kitchenAllocator = kitchenAllocator;
        this.billingModule = billingModule;
        this.stateManager = stateManager;
    }

    // ------------------------------------------------------------------
    // INSPECTION
    // ------------------------------------------------------------------

    /** Returns the persisted chain record, or {@code null} if the order is unknown. */
    public IChainStateManager.ChainStateRecord getChainState(String orderId) {
        return stateManager.findByOrderId(orderId);
    }

    /** Returns every chain that is not finished yet. */
    public Map<String, IChainStateManager.ChainStateRecord> listPendingOrders() {
        return stateManager.getIncompleteTransactions();
    }

    /** Returns the persisted order, or {@code null} if unknown. */
    public IntakeRequest getOrder(String orderId) {
        return orderQueue.getOrder(orderId);
    }

    // ------------------------------------------------------------------
    // MANUAL CANCELLATION
    // ------------------------------------------------------------------

    /**
     * Cancels an order based on its persisted state.
     *
     * @return {@code false} if the order is unknown or already finished; {@code true} if it was cancelled
     */
    public boolean forceCancelOrder(String orderId) {
        IChainStateManager.ChainStateRecord record = stateManager.findByOrderId(orderId);
        if (record == null) {
            System.out.println("[AdminFacade] Unknown order " + orderId + ", nothing to do.");
            return false;
        }
        System.out.println("[AdminFacade] Cancelling order " + orderId + " from state " + record.state);

        switch (record.state) {
            case COMPLETED, PENDING_C -> {
                // Same situation in both cases: the kitchen committed and the customer may have
                // been charged. refundIfPaid() checks the persisted receipt, so money is returned
                // exactly when the payment already went through.
                refundIfPaid(record.ticketId);
                if (record.ticketId != null) {
                    kitchenAllocator.compensateTicket(record.ticketId);
                }
                orderQueue.cancelWithoutRestock(orderId);
                stateManager.updateState(orderId, record.ticketId, ChainState.COMPENSATED_AT_C);
            }
            case STARTED, PENDING_B -> unwindBeforeCheckout(orderId);
            case COMPENSATED_AT_A, COMPENSATED_AT_B, COMPENSATED_AT_C, REJECTED -> {
                System.out.println("[AdminFacade] Order " + orderId + " is already finished (" + record.state + ").");
                return false;
            }
        }
        System.out.println("[AdminFacade] Order " + orderId + " cancelled.");
        return true;
    }

    /**
     * Cancels an order the kitchen never finished. Mirrors the primary facade's recovery: if a
     * meal was already cooked the ingredients are consumed, so they are written off instead of
     * being returned to the inventory; otherwise the reservation is undone with a restock.
     */
    private void unwindBeforeCheckout(String orderId) {
        KitchenTicket ticket = kitchenAllocator.findByOrderId(orderId);
        boolean mealPrepared = ticket != null && ticket.getStatus() == KitchenStatus.QUALITY_PASSED;
        if (ticket != null && ticket.getStatus() != KitchenStatus.WASTED) {
            kitchenAllocator.compensateTicket(ticket.getTicketId());
        }
        if (mealPrepared) {
            orderQueue.cancelWithoutRestock(orderId);
        } else {
            orderQueue.cancelAndRestock(orderId);
        }
        stateManager.updateState(orderId, ticket == null ? null : ticket.getTicketId(), ChainState.COMPENSATED_AT_B);
    }

    private void refundIfPaid(String ticketId) {
        if (ticketId == null) {
            return;
        }
        Receipt receipt = billingModule.findByTicketId(ticketId);
        if (receipt != null && receipt.getStatus() == CheckoutStatus.PAID_AND_DISPATCHED) {
            billingModule.refund(receipt.getReceiptId());
        }
    }
}
