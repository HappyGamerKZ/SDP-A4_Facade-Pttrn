package org.aitu.se.facade;

import org.aitu.se.acceptance.IntakeStatus;
import org.aitu.se.backhouse.KitchenStatus;
import org.aitu.se.checkout.CheckoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of the primary facade.
 *
 * <p>Every scenario asserts the <em>persisted</em> state of all three subsystems, not just the
 * returned object. The recovery tests build a fresh set of subsystem objects on the same database
 * file ({@link FacadeScenario#reopen()}), which reproduces a real process restart.
 */
class RestaurantOrderFacadeTest {

    @TempDir
    Path tempDir;

    private FacadeScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = new FacadeScenario(tempDir.resolve("restaurant.db"));
    }

    // ------------------------------------------------------------------
    // Main flow
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Happy path: every subsystem keeps its committed state")
    void happyPathCommitsEverySubsystem() {
        OrderResult result = scenario.place(FacadeScenario.orderOf("Burger", 1), 2500.0);

        assertTrue(result.isCompleted());
        scenario.assertChainState(result.orderId(), ChainState.COMPLETED);
        scenario.assertOrderStatus(result.orderId(), IntakeStatus.INVENTORY_RESERVED);
        scenario.assertTicketStatus(result.orderId(), KitchenStatus.QUALITY_PASSED);
        scenario.assertReceiptStatus(result.ticketId(), CheckoutStatus.PAID_AND_DISPATCHED);
        scenario.assertStock("Burger", 99);
        assertTrue(scenario.chain.getIncompleteTransactions().isEmpty());
    }

    @Test
    @DisplayName("Step A rejection: nothing is committed in any subsystem")
    void stepARejectionLeavesNothingCommitted() {
        OrderRejectedException failure = assertThrows(OrderRejectedException.class,
                () -> scenario.place(FacadeScenario.orderOf("Burger", 10_000), 2500.0));

        scenario.assertChainState(failure.getOrderId(), ChainState.REJECTED);
        scenario.assertOrderStatus(failure.getOrderId(), IntakeStatus.CANCELLED);
        scenario.assertStock("Burger", 100);
        scenario.assertNoTicket(failure.getOrderId());
        assertTrue(scenario.chain.getIncompleteTransactions().isEmpty());
    }

    @Test
    @DisplayName("Step B failure (dish on the stop-list): step A is compensated and stock returns")
    void stepBFailureCompensatesStepA() {
        OrderResult result = scenario.place(FacadeScenario.orderOf("Cola", 1), 1000.0);

        assertEquals(ChainState.COMPENSATED_AT_B, result.state());
        assertNotNull(result.failureReason());
        scenario.assertChainState(result.orderId(), ChainState.COMPENSATED_AT_B);
        scenario.assertOrderStatus(result.orderId(), IntakeStatus.CANCELLED);
        scenario.assertStock("Cola", 200);
        // The stop-list is checked before any ticket is written, so no orphan ticket is left.
        scenario.assertNoTicket(result.orderId());
    }

    @Test
    @DisplayName("Step C failure (amount over the card limit): step B is scrapped, then step A is undone")
    void stepCFailureCompensatesBThenA() {
        OrderResult result = scenario.place(FacadeScenario.orderOf("Burger", 1), 25_000.0);

        assertEquals(ChainState.COMPENSATED_AT_C, result.state());
        scenario.assertChainState(result.orderId(), ChainState.COMPENSATED_AT_C);
        scenario.assertOrderStatus(result.orderId(), IntakeStatus.CANCELLED);
        scenario.assertTicketStatus(result.orderId(), KitchenStatus.WASTED);
        scenario.assertReceiptStatus(result.ticketId(), CheckoutStatus.CANCELLED);
        // The meal was already cooked, so the ingredients are written off rather than restocked.
        scenario.assertStock("Burger", 99);
    }

    // ------------------------------------------------------------------
    // Recovery after a real restart (new objects, same file)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Reload: an unpaid PENDING_C order is fully unwound after a restart")
    void reloadUnwindsUnpaidPendingC() {
        OrderResult interrupted = scenario.facade.simulateCrashAfterKitchen(FacadeScenario.orderOf("Burger", 1), 2500.0);

        FacadeScenario restarted = scenario.reopen();
        restarted.assertChainState(interrupted.orderId(), ChainState.PENDING_C);
        restarted.assertOrderStatus(interrupted.orderId(), IntakeStatus.INVENTORY_RESERVED);
        restarted.assertTicketStatus(interrupted.orderId(), KitchenStatus.QUALITY_PASSED);
        restarted.assertNoReceipt(interrupted.ticketId());
        assertEquals(1, restarted.chain.getIncompleteTransactions().size());

        restarted.facade.recoverIncompleteTransactions();

        restarted.assertChainState(interrupted.orderId(), ChainState.COMPENSATED_AT_C);
        restarted.assertOrderStatus(interrupted.orderId(), IntakeStatus.CANCELLED);
        restarted.assertTicketStatus(interrupted.orderId(), KitchenStatus.WASTED);
        restarted.assertStock("Burger", 99);
        assertTrue(restarted.chain.getIncompleteTransactions().isEmpty());
    }

    @Test
    @DisplayName("Reload: recovery resumes a chain whose payment was already captured")
    void reloadResumesPaidPendingC() {
        OrderResult interrupted = scenario.facade.simulateCrashAfterKitchen(FacadeScenario.orderOf("Burger", 1), 2500.0);
        // Reproduce the crash window: the bank charged the card, but the chain state was not advanced.
        scenario.billing.processPayment(interrupted.ticketId(), 2500.0);

        FacadeScenario restarted = scenario.reopen();
        restarted.assertChainState(interrupted.orderId(), ChainState.PENDING_C);
        restarted.assertReceiptStatus(interrupted.ticketId(), CheckoutStatus.PAID_AND_DISPATCHED);

        restarted.facade.recoverIncompleteTransactions();

        restarted.assertChainState(interrupted.orderId(), ChainState.COMPLETED);
        restarted.assertReceiptStatus(interrupted.ticketId(), CheckoutStatus.PAID_AND_DISPATCHED);
        restarted.assertTicketStatus(interrupted.orderId(), KitchenStatus.QUALITY_PASSED);
        restarted.assertStock("Burger", 99);
    }

    @Test
    @DisplayName("Reload: a chain interrupted before the kitchen returns its ingredients")
    void reloadUnwindsPendingBWithoutTicket() {
        scenario.orderQueue.acceptNewOrder("ORD-CRASH-B", FacadeScenario.orderOf("Burger", 2));
        scenario.chain.updateState("ORD-CRASH-B", null, ChainState.PENDING_B);

        FacadeScenario restarted = scenario.reopen();
        restarted.assertStock("Burger", 98);

        restarted.facade.recoverIncompleteTransactions();

        restarted.assertChainState("ORD-CRASH-B", ChainState.COMPENSATED_AT_B);
        restarted.assertOrderStatus("ORD-CRASH-B", IntakeStatus.CANCELLED);
        restarted.assertNoTicket("ORD-CRASH-B");
        restarted.assertStock("Burger", 100);
    }

    @Test
    @DisplayName("Reload: a meal that was already prepared is scrapped, not restocked")
    void reloadUnwindsPendingBWithPreparedMeal() {
        scenario.orderQueue.acceptNewOrder("ORD-CRASH-B2", FacadeScenario.orderOf("Burger", 1));
        scenario.kitchen.startCooking("ORD-CRASH-B2", FacadeScenario.orderOf("Burger", 1));
        scenario.chain.updateState("ORD-CRASH-B2", null, ChainState.PENDING_B);

        FacadeScenario restarted = scenario.reopen();
        restarted.facade.recoverIncompleteTransactions();

        restarted.assertChainState("ORD-CRASH-B2", ChainState.COMPENSATED_AT_B);
        restarted.assertTicketStatus("ORD-CRASH-B2", KitchenStatus.WASTED);
        restarted.assertOrderStatus("ORD-CRASH-B2", IntakeStatus.CANCELLED);
        restarted.assertStock("Burger", 99);
    }

    @Test
    @DisplayName("Reload: STARTED without a committed reservation is simply rejected")
    void reloadResolvesStartedWithoutReservation() {
        scenario.chain.updateState("ORD-STARTED", null, ChainState.STARTED);

        FacadeScenario restarted = scenario.reopen();
        restarted.facade.recoverIncompleteTransactions();

        restarted.assertChainState("ORD-STARTED", ChainState.REJECTED);
        assertTrue(restarted.chain.getIncompleteTransactions().isEmpty());
    }

    @Test
    @DisplayName("Reload: STARTED with a committed reservation is compensated at A")
    void reloadCompensatesStartedWithReservation() {
        scenario.orderQueue.acceptNewOrder("ORD-STARTED-2", FacadeScenario.orderOf("Burger", 3));
        scenario.chain.updateState("ORD-STARTED-2", null, ChainState.STARTED);

        FacadeScenario restarted = scenario.reopen();
        restarted.facade.recoverIncompleteTransactions();

        restarted.assertChainState("ORD-STARTED-2", ChainState.COMPENSATED_AT_A);
        restarted.assertOrderStatus("ORD-STARTED-2", IntakeStatus.CANCELLED);
        restarted.assertStock("Burger", 100);
    }

    @Test
    @DisplayName("Reload: recovery of STARTED is idempotent and never double-restocks")
    void reloadStartedRecoveryIsIdempotent() {
        // Reproduce a recovery pass that restocked and cancelled the order, but did not manage to
        // persist its outcome: the chain is still STARTED and the order is already CANCELLED.
        scenario.orderQueue.acceptNewOrder("ORD-STARTED-3", FacadeScenario.orderOf("Burger", 3));
        scenario.orderQueue.cancelAndRestock("ORD-STARTED-3");
        scenario.chain.updateState("ORD-STARTED-3", null, ChainState.STARTED);

        FacadeScenario restarted = scenario.reopen();
        restarted.facade.recoverIncompleteTransactions();

        // The re-run must not mislabel the chain as REJECTED nor return the ingredients a second time.
        restarted.assertChainState("ORD-STARTED-3", ChainState.COMPENSATED_AT_A);
        restarted.assertOrderStatus("ORD-STARTED-3", IntakeStatus.CANCELLED);
        restarted.assertStock("Burger", 100);
    }

    @Test
    @DisplayName("Recovery is a no-op when nothing was interrupted")
    void recoveryIsNoOpWhenNothingPending() {
        assertDoesNotThrow(() -> scenario.facade.recoverIncompleteTransactions());
        assertTrue(scenario.chain.getIncompleteTransactions().isEmpty());
    }
}
