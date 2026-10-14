package org.aitu.se.acceptance;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class IntakeRepository {
    private final String dbUrl; // "JDBC:sqlite:path/file"

    public IntakeRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS intake_orders (\n"
                + " order_id TEXT PRIMARY KEY,\n"
                + " status TEXT NOT NULL,\n"
                + " items_json TEXT NOT NULL\n"
                + ");";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Cant initialize DB -> Intake", e);
        }
    }

    public void save(IntakeRequest request) {
        // UPSERT: enter new record or update existing one on conflict
        String sql = "INSERT INTO intake_orders(order_id, status, items_json) VALUES(?,?,?) "
                + "ON CONFLICT(order_id) DO UPDATE SET status=excluded.status, items_json=excluded.items_json";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, request.getOrderId());
            pstmt.setString(2, request.getStatus().name());
            pstmt.setString(3, serializeItems(request.getItems())); // Превращаем Map в строку
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Cant save to Intake DB", e);
        }
    }

    public IntakeRequest findById(String orderId) {
        String sql = "SELECT status, items_json FROM intake_orders WHERE order_id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                IntakeStatus status = IntakeStatus.valueOf(rs.getString("status"));
                Map<String, Integer> items = deserializeItems(rs.getString("items_json"));
                return new IntakeRequest(orderId, items, status);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cant read from Intake", e);
        }
        return null;
    }

    // helper methods (mini-serializer, to avoid pulling external libraries like Gson)
    private String serializeItems(Map<String, Integer> items) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        return sb.toString();
    }

    private Map<String, Integer> deserializeItems(String data) {
        Map<String, Integer> items = new HashMap<>();
        if (data == null || data.isEmpty()) return items;
        for (String itemStr : data.split(",")) {
            if (!itemStr.isEmpty()) {
                String[] parts = itemStr.split(":");
                items.put(parts[0], Integer.parseInt(parts[1]));
            }
        }
        return items;
    }
}