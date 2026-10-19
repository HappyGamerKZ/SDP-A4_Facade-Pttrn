package org.aitu.se.backhouse;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class KitchenRepository {
    private final String dbUrl;
    // same shit as IntakeRepository
    public KitchenRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS kitchen_tickets (\n"
                + " ticket_id TEXT PRIMARY KEY,\n"
                + " order_id TEXT NOT NULL,\n"
                + " status TEXT NOT NULL,\n"
                + " station_id INTEGER\n"
                + ");";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("cant initialize DB -> Kitchen", e);
        }
    }

    public void save(KitchenTicket ticket) {
        String sql = "INSERT INTO kitchen_tickets(ticket_id, order_id, status, station_id) VALUES(?,?,?,?) "
                + "ON CONFLICT(ticket_id) DO UPDATE SET status=excluded.status, station_id=excluded.station_id";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticket.getTicketId());
            pstmt.setString(2, ticket.getOrderId());
            pstmt.setString(3, ticket.getStatus().name());
            pstmt.setInt(4, ticket.getStationId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("cant save Ticket to Kitchen", e);
        }
    }

    public KitchenTicket findById(String ticketId) {
        String sql = "SELECT order_id, status, station_id FROM kitchen_tickets WHERE ticket_id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                KitchenStatus status = KitchenStatus.valueOf(rs.getString("status"));
                return new KitchenTicket(ticketId, rs.getString("order_id"), status, rs.getInt("station_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("cant read from Kitchen", e);
        }
        return null;
    }

    public List<KitchenTicket> findByOrderId(String orderId) {
        String sql = "SELECT ticket_id, status, station_id FROM kitchen_tickets WHERE order_id = ?";
        List<KitchenTicket> tickets = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                KitchenStatus status = KitchenStatus.valueOf(rs.getString("status"));
                tickets.add(new KitchenTicket(rs.getString("ticket_id"), orderId, status, rs.getInt("station_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("cant read from Kitchen", e);
        }
        return tickets;
    }
}