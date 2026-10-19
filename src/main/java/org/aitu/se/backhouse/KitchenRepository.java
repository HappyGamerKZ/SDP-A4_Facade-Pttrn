package org.aitu.se.backhouse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Persistence gateway for kitchen tickets and the kitchen stop-list. Contains no business rules. */
public class KitchenRepository {
    private final String dbUrl;

    public KitchenRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String tickets = "CREATE TABLE IF NOT EXISTS kitchen_tickets (\n"
                + " ticket_id TEXT PRIMARY KEY,\n"
                + " order_id TEXT NOT NULL,\n"
                + " status TEXT NOT NULL,\n"
                + " station_id INTEGER\n"
                + ");";
        String stopList = "CREATE TABLE IF NOT EXISTS stop_list (dish TEXT PRIMARY KEY);";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(tickets);
            stmt.execute(stopList);
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM stop_list");
            if (rs.getInt(1) == 0) {
                // The kitchen is out of the equipment needed to prepare "Cola" today.
                stmt.execute("INSERT INTO stop_list (dish) VALUES ('Cola')");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot initialize the kitchen database", e);
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
            throw new RuntimeException("Cannot save the kitchen ticket", e);
        }
    }

    public KitchenTicket findById(String ticketId) {
        String sql = "SELECT order_id, status, station_id FROM kitchen_tickets WHERE ticket_id = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ticketId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new KitchenTicket(ticketId, rs.getString("order_id"),
                        KitchenStatus.valueOf(rs.getString("status")), rs.getInt("station_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the kitchen ticket", e);
        }
        return null;
    }

    /** Returns the most recent ticket created for the order, or {@code null} if there is none. */
    public KitchenTicket findByOrderId(String orderId) {
        String sql = "SELECT ticket_id, status, station_id FROM kitchen_tickets WHERE order_id = ? "
                + "ORDER BY rowid DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new KitchenTicket(rs.getString("ticket_id"), orderId,
                        KitchenStatus.valueOf(rs.getString("status")), rs.getInt("station_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the kitchen ticket", e);
        }
        return null;
    }

    public List<KitchenTicket> findAllByOrderId(String orderId) {
        String sql = "SELECT ticket_id, status, station_id FROM kitchen_tickets WHERE order_id = ?";
        List<KitchenTicket> tickets = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                tickets.add(new KitchenTicket(rs.getString("ticket_id"), orderId,
                        KitchenStatus.valueOf(rs.getString("status")), rs.getInt("station_id")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the kitchen tickets", e);
        }
        return tickets;
    }

    public int countByStatus(KitchenStatus status) {
        String sql = "SELECT COUNT(*) FROM kitchen_tickets WHERE status = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            ResultSet rs = pstmt.executeQuery();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException("Cannot count the kitchen tickets", e);
        }
    }

    /** Station ids currently occupied by a ticket in status PREPARING. */
    public List<Integer> occupiedStations() {
        String sql = "SELECT station_id FROM kitchen_tickets WHERE status = ?";
        List<Integer> stations = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, KitchenStatus.PREPARING.name());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                stations.add(rs.getInt("station_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the station occupancy", e);
        }
        return stations;
    }

    public boolean isOnStopList(String dish) {
        String sql = "SELECT 1 FROM stop_list WHERE dish = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dish);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read the stop-list", e);
        }
    }
}
