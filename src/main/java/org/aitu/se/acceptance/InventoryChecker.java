package org.aitu.se.acceptance;

import java.sql.*;
import java.util.Map;

public class InventoryChecker {
    private final String dbUrl;

    public InventoryChecker(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS inventory (ingredient TEXT PRIMARY KEY, qty INTEGER);";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);

            // Если таблица пустая, заполняем базовыми продуктами
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM inventory");
            if (rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO inventory (ingredient, qty) VALUES ('Burger', 100), ('Cola', 200)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Не удалось инициализировать Склад", e);
        }
    }

    public void reserveItems(Map<String, Integer> requiredItems) {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            conn.setAutoCommit(false); // Начинаем транзакцию

            // 1. Проверяем наличие
            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                String checkSql = "SELECT qty FROM inventory WHERE ingredient = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
                    pstmt.setString(1, entry.getKey());
                    ResultSet rs = pstmt.executeQuery();
                    if (!rs.next() || rs.getInt("qty") < entry.getValue()) {
                        conn.rollback();
                        throw new OutOfStockException("Недостаточно на складе: " + entry.getKey());
                    }
                }
            }

            // 2. Списываем
            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                String updateSql = "UPDATE inventory SET qty = qty - ? WHERE ingredient = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setInt(1, entry.getValue());
                    pstmt.setString(2, entry.getKey());
                    pstmt.executeUpdate();
                    System.out.println("[Inventory] Зарезервировано: " + entry.getValue() + " шт. " + entry.getKey());
                }
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка работы со складом", e);
        }
    }

    public void returnItems(Map<String, Integer> returnedItems) {
        String updateSql = "UPDATE inventory SET qty = qty + ? WHERE ingredient = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
            for (Map.Entry<String, Integer> entry : returnedItems.entrySet()) {
                pstmt.setInt(1, entry.getValue());
                pstmt.setString(2, entry.getKey());
                pstmt.executeUpdate();
                System.out.println("[Inventory] Возвращено на склад: " + entry.getValue() + " шт. " + entry.getKey());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка возврата на склад", e);
        }
    }
}