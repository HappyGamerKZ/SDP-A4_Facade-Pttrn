package org.aitu.se.facade;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Durable store of the facade's chain progress. */
public class ChainStateManager implements IChainStateManager {
    private final String dbUrl;

    public ChainStateManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS chain_state (\n"
                + " order_id TEXT PRIMARY KEY,\n"
                + " ticket_id TEXT,\n"
                + " state TEXT NOT NULL\n"
                + ");";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Cannot initialize the facade state database", e);
        }
    }

    @Override
    public void updateState(String orderId, String ticketId, ChainState state) {
        String sql = "INSERT INTO chain_state(order_id, ticket_id, state) VALUES(?,?,?) "
                + "ON CONFLICT(order_id) DO UPDATE SET ticket_id=excluded.ticket_id, state=excluded.state";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, orderId);
            pstmt.setString(2, ticketId);
            pstmt.setString(3, state.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot persist the chain state", e);
        }
    }

    @Override
    public Map<String, ChainStateRecord> getIncompleteTransactions() {
        Map<String, ChainStateRecord> incomplete = new HashMap<>();
        String sql = "SELECT order_id, ticket_id, state FROM chain_state "
                + "WHERE state IN ('STARTED', 'PENDING_B', 'PENDING_C')";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String orderId = rs.getString("order_id");
                incomplete.put(orderId, new ChainStateRecord(orderId, rs.getString("ticket_id"),
                        ChainState.valueOf(rs.getString("state"))));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the interrupted transactions", e);
        }
        return incomplete;
    }

    @Override
    public ChainStateRecord findByOrderId(String orderId) {
        String sql = "SELECT ticket_id, state FROM chain_state WHERE order_id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new ChainStateRecord(orderId, rs.getString("ticket_id"),
                        ChainState.valueOf(rs.getString("state")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the chain state", e);
        }
        return null;
    }
}
