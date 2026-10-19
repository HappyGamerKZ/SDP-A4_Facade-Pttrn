package org.aitu.se;

import org.aitu.se.acceptance.IntakeStatus;
import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;
import org.aitu.se.backhouse.KitchenRepository;
import org.aitu.se.backhouse.KitchenStatus;
import org.aitu.se.backhouse.KitchenTicket;
import org.aitu.se.backhouse.NoAvailableStationsException;
import org.aitu.se.checkout.BillingModule;
import org.aitu.se.checkout.CheckoutStatus;
import org.aitu.se.checkout.Receipt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that every subsystem is usable on its own: each one is wired to its own database and is
 * driven through its own public interface, without the facade and without the other subsystems.
 * This is the "usable on their own" half of the subsystem-independence requirement.
 */
class SubsystemDirectUseTest {

    @TempDir
    Path tempDir;

    private String dbUrl(String name) {
        return "jdbc:sqlite:" + tempDir.resolve(name).toAbsolutePath().toString().replace('\\', '/');
    }

    private static Map<String, Integer> orderOf(String dish, int qty) {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put(dish, qty);
        return items;
    }

    @Test
    @DisplayName("Subsystem A works on its own database")
    void acceptanceAlone() {
        OrderQueue queue = new OrderQueue(dbUrl("a-intake.db"), dbUrl("a-inventory.db"));

        queue.acceptNewOrder("ORD-A", orderOf("Burger", 2));

        assertEquals(IntakeStatus.INVENTORY_RESERVED, queue.getOrder("ORD-A").getStatus());
        assertEquals(98, queue.stockOf("Burger"));
    }

    @Test
    @DisplayName("Subsystem B works on its own database and can compensate a ticket")
    void backhouseAlone() {
        CookingStationAllocator kitchen = new CookingStationAllocator(dbUrl("b.db"));

        String ticketId = kitchen.startCooking("ORD-B", orderOf("Burger", 1));

        KitchenTicket ticket = kitchen.findByOrderId("ORD-B");
        assertNotNull(ticket);
        assertEquals(ticketId, ticket.getTicketId());
        assertEquals(KitchenStatus.QUALITY_PASSED, ticket.getStatus());

        kitchen.compensateTicket(ticketId);
        assertEquals(KitchenStatus.WASTED, kitchen.findByOrderId("ORD-B").getStatus());
    }

    @Test
    @DisplayName("Subsystem B refuses to cook when every station is already busy")
    void backhouseRunsOutOfStations() {
        String url = dbUrl("b-full.db");
        KitchenRepository repository = new KitchenRepository(url);
        for (int station = 1; station <= CookingStationAllocator.MAX_STATIONS; station++) {
            repository.save(new KitchenTicket("TCK-BUSY-" + station, "ORD-BUSY-" + station,
                    KitchenStatus.PREPARING, station));
        }
        CookingStationAllocator kitchen = new CookingStationAllocator(url);

        assertThrows(NoAvailableStationsException.class,
                () -> kitchen.startCooking("ORD-NEW", orderOf("Burger", 1)));
    }

    @Test
    @DisplayName("Subsystem C works on its own database and can refund")
    void checkoutAlone() {
        BillingModule billing = new BillingModule(dbUrl("c.db"));

        String receiptId = billing.processPayment("TCK-C", 1500.0);

        Receipt receipt = billing.findByTicketId("TCK-C");
        assertEquals(CheckoutStatus.PAID_AND_DISPATCHED, receipt.getStatus());
        assertEquals(receiptId, receipt.getReceiptId());

        billing.refund(receiptId);
        assertEquals(CheckoutStatus.REFUNDED, billing.findByTicketId("TCK-C").getStatus());
    }
}
