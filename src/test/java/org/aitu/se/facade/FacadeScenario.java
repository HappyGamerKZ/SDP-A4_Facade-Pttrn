package org.aitu.se.facade;

import org.aitu.se.acceptance.IntakeRequest;
import org.aitu.se.acceptance.IntakeStatus;
import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;
import org.aitu.se.backhouse.KitchenStatus;
import org.aitu.se.backhouse.KitchenTicket;
import org.aitu.se.checkout.BillingModule;
import org.aitu.se.checkout.CheckoutStatus;
import org.aitu.se.checkout.Receipt;
import org.aitu.se.facade.IChainStateManager.ChainStateRecord;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test harness and mini-DSL for the whole restaurant.
 *
 * <p>Every assertion reads the <em>persisted</em> state through the subsystem gateways, never an
 * in-memory shortcut. {@link #reopen()} builds brand-new objects on the same database file, which
 * makes a real "close and reopen the store" scenario possible.
 */
class FacadeScenario {

    final OrderQueue orderQueue;
    final CookingStationAllocator kitchen;
    final BillingModule billing;
    final ChainStateManager chain;
    final RestaurantOrderFacade facade;
    final AdminCompensationFacade admin;

    private final Path dbFile;

    FacadeScenario(Path dbFile) {
        this.dbFile = dbFile;
        String dbUrl = url(dbFile);
        this.orderQueue = new OrderQueue(dbUrl);
        this.kitchen = new CookingStationAllocator(dbUrl);
        this.billing = new BillingModule(dbUrl);
        this.chain = new ChainStateManager(dbUrl);
        this.facade = new RestaurantOrderFacade(orderQueue, kitchen, billing, chain);
        this.admin = new AdminCompensationFacade(orderQueue, kitchen, billing, chain);
    }

    /** Simulates a process restart: brand-new objects on the very same database file. */
    FacadeScenario reopen() {
        return new FacadeScenario(dbFile);
    }

    private static String url(Path dbFile) {
        return "jdbc:sqlite:" + dbFile.toAbsolutePath().toString().replace('\\', '/');
    }

    // ------------------------------------------------------------------
    // given / when
    // ------------------------------------------------------------------

    static Map<String, Integer> orderOf(String dish, int qty) {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put(dish, qty);
        return items;
    }

    static Map<String, Integer> orderOf(String dishA, int qtyA, String dishB, int qtyB) {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put(dishA, qtyA);
        items.put(dishB, qtyB);
        return items;
    }

    OrderResult place(Map<String, Integer> items, double amount) {
        return facade.processOrder(items, amount);
    }

    // ------------------------------------------------------------------
    // assertions over persisted state
    // ------------------------------------------------------------------

    void assertChainState(String orderId, ChainState expected) {
        ChainStateRecord record = chain.findByOrderId(orderId);
        assertNotNull(record, "no chain record for " + orderId);
        assertEquals(expected, record.state, "unexpected chain state for " + orderId);
    }

    void assertOrderStatus(String orderId, IntakeStatus expected) {
        IntakeRequest request = orderQueue.getOrder(orderId);
        assertNotNull(request, "no intake order for " + orderId);
        assertEquals(expected, request.getStatus(), "unexpected intake status for " + orderId);
    }

    void assertTicketStatus(String orderId, KitchenStatus expected) {
        KitchenTicket ticket = kitchen.findByOrderId(orderId);
        assertNotNull(ticket, "no kitchen ticket for " + orderId);
        assertEquals(expected, ticket.getStatus(), "unexpected ticket status for " + orderId);
    }

    void assertNoTicket(String orderId) {
        assertNull(kitchen.findByOrderId(orderId), "an orphan kitchen ticket was left for " + orderId);
    }

    void assertReceiptStatus(String ticketId, CheckoutStatus expected) {
        Receipt receipt = billing.findByTicketId(ticketId);
        assertNotNull(receipt, "no receipt for ticket " + ticketId);
        assertEquals(expected, receipt.getStatus(), "unexpected receipt status for ticket " + ticketId);
    }

    void assertNoReceipt(String ticketId) {
        assertNull(billing.findByTicketId(ticketId), "an orphan receipt was left for ticket " + ticketId);
    }

    void assertStock(String dish, int expected) {
        assertEquals(expected, orderQueue.stockOf(dish), "unexpected stock for " + dish);
    }
}
