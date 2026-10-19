package org.aitu.se;

import org.aitu.se.acceptance.IntakeRequest;
import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;
import org.aitu.se.backhouse.KitchenTicket;
import org.aitu.se.checkout.BillingModule;
import org.aitu.se.checkout.Receipt;
import org.aitu.se.facade.AdminCompensationFacade;
import org.aitu.se.facade.ChainState;
import org.aitu.se.facade.IChainStateManager;
import org.aitu.se.facade.IChainStateManager.ChainStateRecord;
import org.aitu.se.facade.OrderRejectedException;
import org.aitu.se.facade.OrderResult;
import org.aitu.se.facade.RestaurantOrderFacade;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Interactive demo.
 *
 * <p>This class is the composition root: it is the only place that knows every subsystem and its
 * database. The interactive client below talks to the two facades only. All persistent state lives
 * under {@code ./data}; delete that folder to reset the demo.
 */
public class Main {

    private static final String DATA_DIR = "data";

    public static void main(String[] args) {
        OrderQueue orderQueue =
                new OrderQueue("jdbc:sqlite:" + DATA_DIR + "/acceptance.db", "jdbc:sqlite:" + DATA_DIR + "/inventory.db");
        CookingStationAllocator kitchen = new CookingStationAllocator("jdbc:sqlite:" + DATA_DIR + "/backhouse.db");
        BillingModule billing = new BillingModule("jdbc:sqlite:" + DATA_DIR + "/checkout.db");
        IChainStateManager chain = new org.aitu.se.facade.ChainStateManager("jdbc:sqlite:" + DATA_DIR + "/chain.db");

        RestaurantOrderFacade facade = new RestaurantOrderFacade(orderQueue, kitchen, billing, chain);
        AdminCompensationFacade adminFacade = new AdminCompensationFacade(orderQueue, kitchen, billing, chain);

        // Finish or undo whatever a previous run left half-way.
        facade.recoverIncompleteTransactions();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            printMenu();
            int choice = readInt(scanner, "Choose an option: ");
            switch (choice) {
                case 1 -> placeOrder(scanner, facade);
                case 2 -> showOrder(scanner, orderQueue, kitchen, billing, adminFacade);
                case 3 -> listPending(adminFacade);
                case 4 -> adminCancel(scanner, adminFacade);
                case 5 -> simulateCrash(scanner, facade);
                case 0 -> {
                    System.out.println("Bye.");
                    return;
                }
                default -> System.out.println("Unknown option, try again.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n--- RESTAURANT KITCHEN (Facade demo) ---");
        System.out.println("1. Place an order");
        System.out.println("2. Show the persisted state of an order");
        System.out.println("3. List pending (interrupted) orders");
        System.out.println("4. Admin: force-cancel an order");
        System.out.println("5. Simulate a crash after the kitchen step");
        System.out.println("0. Exit");
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private static void placeOrder(Scanner scanner, RestaurantOrderFacade facade) {
        Map<String, Integer> items = readItems(scanner);
        if (items == null) {
            return;
        }
        double amount = readDouble(scanner, "Enter the amount to charge: ");
        try {
            printResult(facade.processOrder(items, amount));
        } catch (OrderRejectedException e) {
            System.out.println("[Client] The order was rejected: " + e.getMessage());
        }
    }

    private static void showOrder(Scanner scanner, OrderQueue orderQueue, CookingStationAllocator kitchen,
                                  BillingModule billing, AdminCompensationFacade adminFacade) {
        String orderId = readLine(scanner, "Enter the order id: ");
        ChainStateRecord record = adminFacade.getChainState(orderId);
        if (record == null) {
            System.out.println("[Client] Unknown order " + orderId);
            return;
        }
        System.out.println("[Client] Chain state    : " + record.state);
        System.out.println("[Client] Ticket id      : " + record.ticketId);

        IntakeRequest request = orderQueue.getOrder(orderId);
        if (request != null) {
            System.out.println("[Client] Order status   : " + request.getStatus());
            request.getItems().forEach((dish, qty) ->
                    System.out.println("[Client]   " + dish + " x" + qty + " (stock left: " + orderQueue.stockOf(dish) + ")"));
        }
        if (record.ticketId != null) {
            KitchenTicket ticket = kitchen.findByOrderId(orderId);
            if (ticket != null) {
                System.out.println("[Client] Kitchen ticket : " + ticket.getStatus() + " (station #" + ticket.getStationId() + ")");
            }
            Receipt receipt = billing.findByTicketId(record.ticketId);
            if (receipt != null) {
                System.out.println("[Client] Receipt        : " + receipt.getStatus() + " (" + receipt.getAmount() + ")");
            }
        }
    }

    private static void listPending(AdminCompensationFacade adminFacade) {
        Map<String, ChainStateRecord> pending = adminFacade.listPendingOrders();
        if (pending.isEmpty()) {
            System.out.println("[Client] No pending orders.");
            return;
        }
        pending.values().forEach(record ->
                System.out.println("[Client] " + record.orderId + " -> " + record.state + " (ticket " + record.ticketId + ")"));
    }

    private static void adminCancel(Scanner scanner, AdminCompensationFacade adminFacade) {
        String orderId = readLine(scanner, "Enter the order id to cancel: ");
        boolean cancelled = adminFacade.forceCancelOrder(orderId);
        System.out.println("[Client] " + (cancelled ? "The order was cancelled." : "Nothing was cancelled."));
    }

    private static void simulateCrash(Scanner scanner, RestaurantOrderFacade facade) {
        Map<String, Integer> items = readItems(scanner);
        if (items == null) {
            return;
        }
        double amount = readDouble(scanner, "Enter the amount to charge: ");
        OrderResult result = facade.simulateCrashAfterKitchen(items, amount);
        System.out.println("[Client] The process is aborting. Restart the app to watch recovery, order id: " + result.orderId());
    }

    // ------------------------------------------------------------------
    // Input helpers
    // ------------------------------------------------------------------

    private static void printResult(OrderResult result) {
        System.out.println("[Client] Order " + result.orderId() + " -> " + result.state()
                + (result.failureReason() != null ? " (reason: " + result.failureReason() + ")" : ""));
    }

    /** Reads dishes in the form {@code Name qty, Name qty} (for example {@code Burger 2, Cola 1}). */
    private static Map<String, Integer> readItems(Scanner scanner) {
        while (true) {
            String line = readLine(scanner, "Enter items as 'Name qty, Name qty' (for example 'Burger 2, Cola 1'): ");
            if (line.isEmpty()) {
                System.out.println("Please enter at least one item.");
                continue;
            }
            Map<String, Integer> items = new LinkedHashMap<>();
            boolean valid = true;
            for (String part : line.split(",")) {
                String[] tokens = part.trim().split("\\s+");
                if (tokens.length != 2) {
                    System.out.println("Bad item '" + part.trim() + "'. Expected 'Name qty'.");
                    valid = false;
                    break;
                }
                Integer qty = parseInt(tokens[1]);
                if (qty == null || qty <= 0) {
                    System.out.println("Bad quantity for '" + tokens[0] + "'. It must be a positive integer.");
                    valid = false;
                    break;
                }
                items.merge(tokens[0], qty, Integer::sum);
            }
            if (valid && !items.isEmpty()) {
                return items;
            }
        }
    }

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            Integer value = parseInt(readLine(scanner, prompt));
            if (value != null) {
                return value;
            }
            System.out.println("Please enter a whole number.");
        }
    }

    private static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            String line = readLine(scanner, prompt);
            try {
                return Double.parseDouble(line.replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }

    private static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
