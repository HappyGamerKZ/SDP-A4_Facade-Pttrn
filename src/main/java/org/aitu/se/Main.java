package org.aitu.se;

import org.aitu.se.acceptance.OrderQueue;
import org.aitu.se.backhouse.CookingStationAllocator;
import org.aitu.se.checkout.BillingModule;
import org.aitu.se.facade.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // 1. Создаем экземпляры с путями к БД (Dependency Injection)
        // Для тестов можно будет передавать "jdbc:sqlite:test-data/acceptance.db"
        IOrderQueue q = new OrderQueue("jdbc:sqlite:data/acceptance.db", "jdbc:sqlite:data/inv_check.db");
        ICookingStationAllocator k = new CookingStationAllocator("jdbc:sqlite:data/backhouse.db");
        IBillingModule b = new BillingModule("jdbc:sqlite:data/checkout.db");
        IChainStateManager sm = new ChainStateManager("jdbc:sqlite:data/chain.db");

        // 2. Инициализируем Фасад
        RestaurantOrderFacade facade = new RestaurantOrderFacade(q, k, b, sm);

        // 3. Вызываем восстановление при каждом запуске! (Требование 3.3 выполнено)
        facade.recoverIncompleteTransactions();

        Scanner scanner = new Scanner(System.in);
        Map<String, Integer> defaultOrder = new HashMap<>();
        defaultOrder.put("Burger", 2);

        while (true) {
            System.out.println("\n--- РЕСТОРАННАЯ КУХНЯ ---");
            System.out.println("1. Успешный заказ (Happy path)");
            System.out.println("2. Сбой на кухне (Откат Шага А)");
            System.out.println("3. Сбой оплаты (Откат Шагов В и А)");
            System.out.println("4. Краш сервера на середине (Persistence-reload test)");
            System.out.println("0. Выход");
            System.out.print("Выберите действие: ");

            int choice = scanner.nextInt();
            if (choice == 0) break;

            try {
                switch (choice) {
                    case 1:
                        printResult(facade.processOrder(defaultOrder, 5000.0, false, false, false));
                        break;
                    case 2:
                        printResult(facade.processOrder(defaultOrder, 5000.0, true, false, false));
                        break;
                    case 3:
                        printResult(facade.processOrder(defaultOrder, 5000.0, false, true, false));
                        break;
                    case 4:
                        // Это закроет пункт 3.7 "persistence-reload test"
                        printResult(facade.processOrder(defaultOrder, 5000.0, false, false, true));
                        System.out.println("ПРОГРАММА ПРЕРВАНА. Перезапустите приложение, чтобы увидеть восстановление.");
                        return; // Реальный выход из программы для симуляции!
                }
            } catch (OrderRejectedException e) {
                System.out.println("[Client] Заказ не принят: " + e.getMessage());
            }
        }
    }

    private static void printResult(OrderResult result) {
        System.out.println("[Client] Заказ " + result.orderId() + " -> " + result.state()
                + (result.failureReason() != null ? " (причина: " + result.failureReason() + ")" : ""));
    }
}