package org.aitu.se.acceptance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistence gateway for the inventory. Owns the JDBC state, contains no business rules.
 * The stock seeded on first start models the restaurant's opening inventory.
 */
public class InventoryRepository {
    private final String dbUrl;

    public InventoryRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS inventory (ingredient TEXT PRIMARY KEY, qty INTEGER NOT NULL);";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM inventory");
            if (rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO inventory (ingredient, qty) VALUES ('Burger', 100), ('Cola', 200), ('Fries', 150)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot initialize the inventory database", e);
        }
    }

    /** Returns the whole stock as an ingredient -> quantity map. */
    public Map<String, Integer> snapshot() {
        Map<String, Integer> stock = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT ingredient, qty FROM inventory")) {
            while (rs.next()) {
                stock.put(rs.getString("ingredient"), rs.getInt("qty"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the inventory snapshot", e);
        }
        return stock;
    }

    public int getQuantity(String ingredient) {
        String sql = "SELECT qty FROM inventory WHERE ingredient = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ingredient);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? rs.getInt("qty") : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read stock for '" + ingredient + "'", e);
        }
    }

    /** Subtracts the given quantities from the stock. */
    public void decrement(Map<String, Integer> items) {
        applyDelta(items, -1);
    }

    /** Adds the given quantities back to the stock. */
    public void increment(Map<String, Integer> items) {
        applyDelta(items, 1);
    }

    private void applyDelta(Map<String, Integer> items, int sign) {
        String sql = "UPDATE inventory SET qty = qty + ? WHERE ingredient = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : items.entrySet()) {
                pstmt.setInt(1, sign * entry.getValue());
                pstmt.setString(2, entry.getKey());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot update the inventory", e);
        }
    }
}
