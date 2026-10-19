package org.aitu.se.facade;

import org.aitu.se.acceptance.IntakeStatus;
import org.aitu.se.backhouse.KitchenStatus;
import org.aitu.se.checkout.CheckoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the administrative facade. The assertions check the persisted state, so a "successful"
 * cancellation is one that actually changed the records of the subsystems.
 */
class AdminCompensationFacadeTest {

    @TempDir
    Path tempDir;

    private FacadeScenario scenario;

    @BeforeEach
    void setUp() {
        scenario = new FacadeScenario(tempDir.resolve("restaurant.db"));
    }

    @Test
    @DisplayName("Inspection reads the persisted state of a completed order")
    void inspectionOfCompletedOrder() {
        OrderResult result = scenario.place(FacadeScenario.orderOf("Burger", 1), 2500.0);

        assertEquals(ChainState.COMPLETED, scenario.admin.getChainState(result.orderId()).state);
        assertEquals(IntakeStatus.INVENTORY_RESERVED, scenario.admin.getOrder(result.orderId()).getStatus());
        assertTrue(scenario.admin.listPendingOrders().isEmpty());
    }

    @Test
    @DisplayName("Unknown orders are reported, never silently 'cancelled'")
    void unknownOrderIsReported() {
        assertNull(scenario.admin.getChainState("NO-SUCH-ORDER"));
        assertFalse(scenario.admin.forceCancelOrder("NO-SUCH-ORDER"));
    }

    @Test
    @DisplayName("Force-cancel of a paid order refunds the customer and scraps the meal")
    void forceCancelRefundsPaidOrder() {
        OrderResult result = scenario.place(FacadeScenario.orderOf("Burger", 1), 2500.0);

        assertTrue(scenario.admin.forceCancelOrder(result.orderId()));

        scenario.assertChainState(result.orderId(), ChainState.COMPENSATED_AT_C);
        scenario.assertOrderStatus(result.orderId(), IntakeStatus.CANCELLED);
        scenario.assertTicketStatus(result.orderId(), KitchenStatus.WASTED);
        scenario.assertReceiptStatus(result.ticketId(), CheckoutStatus.REFUNDED);
        scenario.assertStock("Burger", 99);
    }

    @Test
    @DisplayName("Force-cancel of an already finished order changes nothing")
    void forceCancelOfFinishedOrder() {
        OrderResult result = scenario.place(FacadeScenario.orderOf("Cola", 1), 1000.0);
        assertEquals(ChainState.COMPENSATED_AT_B, result.state());

        assertFalse(scenario.admin.forceCancelOrder(result.orderId()));
        scenario.assertChainState(result.orderId(), ChainState.COMPENSATED_AT_B);
        scenario.assertStock("Cola", 200);
    }

    @Test
    @DisplayName("Force-cancel of an interrupted order unwinds it and restocks the ingredients")
    void forceCancelOfInterruptedPendingB() {
        scenario.orderQueue.acceptNewOrder("ORD-ADMIN-B", FacadeScenario.orderOf("Burger", 2));
        scenario.chain.updateState("ORD-ADMIN-B", null, ChainState.PENDING_B);

        assertTrue(scenario.admin.forceCancelOrder("ORD-ADMIN-B"));

        scenario.assertChainState("ORD-ADMIN-B", ChainState.COMPENSATED_AT_B);
        scenario.assertOrderStatus("ORD-ADMIN-B", IntakeStatus.CANCELLED);
        scenario.assertStock("Burger", 100);
    }

    @Test
    @DisplayName("Force-cancel of an interrupted PENDING_C order scraps the prepared meal")
    void forceCancelOfInterruptedPendingC() {
        OrderResult interrupted = scenario.facade.simulateCrashAfterKitchen(FacadeScenario.orderOf("Burger", 1), 2500.0);

        assertTrue(scenario.admin.forceCancelOrder(interrupted.orderId()));

        scenario.assertChainState(interrupted.orderId(), ChainState.COMPENSATED_AT_C);
        scenario.assertTicketStatus(interrupted.orderId(), KitchenStatus.WASTED);
        scenario.assertStock("Burger", 99);
    }

    @Test
    @DisplayName("Inspection lists every interrupted chain")
    void inspectionListsInterruptedChains() {
        scenario.facade.simulateCrashAfterKitchen(FacadeScenario.orderOf("Burger", 1), 2500.0);

        assertEquals(1, scenario.admin.listPendingOrders().size());
    }
}
