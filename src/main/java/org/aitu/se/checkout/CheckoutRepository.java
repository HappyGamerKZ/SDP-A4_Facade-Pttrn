package org.aitu.se.checkout;

import java.sql.*;

public class CheckoutRepository {
    private final String dbUrl;
    // same shit as IntakeRepository
    public CheckoutRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS receipts (\n"
                + " receipt_id TEXT PRIMARY KEY,\n"
                + " ticket_id TEXT NOT NULL,\n"
                + " amount REAL NOT NULL,\n"
                + " status TEXT NOT NULL\n"
                + ");";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("cant initialize DB -> Checkout", e);
        }
    }

    public void save(Receipt receipt) {
        String sql = "INSERT INTO receipts(receipt_id, ticket_id, amount, status) VALUES(?,?,?,?) "
                + "ON CONFLICT(receipt_id) DO UPDATE SET status=excluded.status";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, receipt.getReceiptId());
            pstmt.setString(2, receipt.getTicketId());
            pstmt.setDouble(3, receipt.getAmount());
            pstmt.setString(4, receipt.getStatus().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("cant save Receipt to Checkout", e);
        }
    }
}