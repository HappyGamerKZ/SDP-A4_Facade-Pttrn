package org.aitu.se.checkout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Persistence gateway for receipts. Owns the JDBC state, contains no business rules. */
public class CheckoutRepository {
    private final String dbUrl;

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
            throw new RuntimeException("Cannot initialize the checkout database", e);
        }
    }

    public void save(Receipt receipt) {
        String sql = "INSERT INTO receipts(receipt_id, ticket_id, amount, status) VALUES(?,?,?,?) "
                + "ON CONFLICT(receipt_id) DO UPDATE SET status=excluded.status, amount=excluded.amount";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, receipt.getReceiptId());
            pstmt.setString(2, receipt.getTicketId());
            pstmt.setDouble(3, receipt.getAmount());
            pstmt.setString(4, receipt.getStatus().name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot save the receipt", e);
        }
    }

    public Receipt findById(String receiptId) {
        String sql = "SELECT ticket_id, amount, status FROM receipts WHERE receipt_id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, receiptId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Receipt(receiptId, rs.getString("ticket_id"), rs.getDouble("amount"),
                        CheckoutStatus.valueOf(rs.getString("status")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the receipt", e);
        }
        return null;
    }

    /** Returns the receipt created for the ticket, or {@code null} if there is none. */
    public Receipt findByTicketId(String ticketId) {
        String sql = "SELECT receipt_id, amount, status FROM receipts WHERE ticket_id = ? "
                + "ORDER BY rowid DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Receipt(rs.getString("receipt_id"), ticketId, rs.getDouble("amount"),
                        CheckoutStatus.valueOf(rs.getString("status")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the receipt", e);
        }
        return null;
    }
}
