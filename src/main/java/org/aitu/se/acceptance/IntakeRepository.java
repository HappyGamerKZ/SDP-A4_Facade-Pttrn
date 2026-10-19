package org.aitu.se.acceptance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistence gateway for intake orders. Owns the JDBC state, contains no business rules. */
public class IntakeRepository {
    private final String dbUrl;

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
            throw new RuntimeException("Cannot initialize the intake database", e);
        }
    }

    public void save(IntakeRequest request) {
        String sql = "INSERT INTO intake_orders(order_id, status, items_json) VALUES(?,?,?) "
                + "ON CONFLICT(order_id) DO UPDATE SET status=excluded.status, items_json=excluded.items_json";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, request.getOrderId());
            pstmt.setString(2, request.getStatus().name());
            pstmt.setString(3, serializeItems(request.getItems()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot save the intake order", e);
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
            throw new RuntimeException("Cannot read the intake order", e);
        }
        return null;
    }

    // A tiny escaped serializer, so dish names containing ':' or ',' do not corrupt the payload.
    private String serializeItems(Map<String, Integer> items) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(escape(entry.getKey())).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    private Map<String, Integer> deserializeItems(String data) {
        Map<String, Integer> items = new HashMap<>();
        if (data == null || data.isEmpty()) {
            return items;
        }
        for (String itemStr : splitUnescaped(data, ',')) {
            if (itemStr.isEmpty()) {
                continue;
            }
            List<String> keyValue = splitUnescaped(itemStr, ':');
            if (keyValue.size() != 2) {
                throw new RuntimeException("Corrupted items payload: " + data);
            }
            items.put(keyValue.get(0), Integer.parseInt(keyValue.get(1)));
        }
        return items;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace(":", "\\:").replace(",", "\\,");
    }

    private static List<String> splitUnescaped(String data, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == delimiter) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }
}
