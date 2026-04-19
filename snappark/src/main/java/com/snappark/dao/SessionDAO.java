package com.snappark.dao;

import com.snappark.model.ParkingSession;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SessionDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public String generateSessionPin() {
        return String.valueOf(1000 + new Random().nextInt(9000)); // 4-digit
    }

    public String generateExitPin() {
        return String.valueOf(100000 + new Random().nextInt(900000)); // 6-digit
    }

    public ParkingSession startSession(int vehicleId, int slotId, int userId) {
        return startSession(vehicleId, slotId, userId, "");
    }

    public ParkingSession startSession(int vehicleId, int slotId, int userId, String phoneNumber) {
        String pin = generateSessionPin();
        String sql = "INSERT INTO parking_sessions (vehicle_id, slot_id, user_id, entry_time, active, phone_number, session_pin, payment_status) VALUES (?, ?, ?, ?, 1, ?, ?, 'PENDING')";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, vehicleId);
            ps.setInt(2, slotId);
            ps.setInt(3, userId);
            ps.setString(4, LocalDateTime.now().toString());
            ps.setString(5, phoneNumber != null ? phoneNumber : "");
            ps.setString(6, pin);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                ParkingSession session = new ParkingSession();
                session.setId(keys.getInt(1));
                session.setVehicleId(vehicleId);
                session.setSlotId(slotId);
                session.setUserId(userId);
                session.setEntryTime(LocalDateTime.now());
                session.setActive(true);
                session.setPhoneNumber(phoneNumber != null ? phoneNumber : "");
                session.setSessionPin(pin);
                session.setPaymentStatus("PENDING");
                return session;
            }
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public ParkingSession findActiveBySlot(int slotId) {
        String sql = "SELECT * FROM parking_sessions WHERE slot_id = ? AND active = 1";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, slotId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public ParkingSession findActiveByVehicle(int vehicleId) {
        String sql = "SELECT * FROM parking_sessions WHERE vehicle_id = ? AND active = 1";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, vehicleId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public ParkingSession findActiveByPlate(String plate) {
        String sql = "SELECT ps.* FROM parking_sessions ps " +
                     "JOIN vehicles v ON v.id = ps.vehicle_id " +
                     "WHERE v.license_plate = ? AND ps.active = 1";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, plate.toUpperCase().replaceAll("[\\s\\-]", ""));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public ParkingSession findActiveBySessionPin(String pin) {
        String sql = "SELECT * FROM parking_sessions WHERE session_pin = ? AND active = 1";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pin);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public ParkingSession findByExitPin(String pin) {
        String sql = "SELECT * FROM parking_sessions WHERE exit_pin = ? AND payment_status = 'PAID'";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pin);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public boolean setExitPin(int sessionId, String exitPin) {
        String sql = "UPDATE parking_sessions SET exit_pin = ?, payment_status = 'PAID' WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exitPin);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return false;
    }

    public boolean endSession(int sessionId, LocalDateTime exitTime) {
        String sql = "UPDATE parking_sessions SET exit_time = ?, active = 0 WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, exitTime.toString());
            ps.setInt(2, sessionId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return false;
    }

    public List<ParkingSession> findAllActive() {
        List<ParkingSession> list = new ArrayList<>();
        String sql = "SELECT * FROM parking_sessions WHERE active = 1";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return list;
    }

    public ParkingSession findById(int id) {
        String sql = "SELECT * FROM parking_sessions WHERE id = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public List<ParkingSession> findByPhone(String phone) {
        List<ParkingSession> list = new ArrayList<>();
        String sql = "SELECT * FROM parking_sessions WHERE phone_number = ? ORDER BY entry_time DESC";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone.replaceAll("[^0-9]", ""));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { System.err.println("findByPhone: " + e.getMessage()); }
        return list;
    }

    private ParkingSession mapRow(ResultSet rs) throws SQLException {
        ParkingSession s = new ParkingSession();
        s.setId(rs.getInt("id"));
        s.setVehicleId(rs.getInt("vehicle_id"));
        s.setSlotId(rs.getInt("slot_id"));
        s.setUserId(rs.getInt("user_id"));
        s.setEntryTime(LocalDateTime.parse(rs.getString("entry_time")));
        String exit = rs.getString("exit_time");
        if (exit != null) s.setExitTime(LocalDateTime.parse(exit));
        s.setActive(rs.getInt("active") == 1);
        // v2.0 fields — safe get
        try { s.setPhoneNumber(rs.getString("phone_number")); } catch (Exception ignored) {}
        try { s.setSessionPin(rs.getString("session_pin")); } catch (Exception ignored) {}
        try { s.setExitPin(rs.getString("exit_pin")); } catch (Exception ignored) {}
        try { s.setPaymentStatus(rs.getString("payment_status")); } catch (Exception ignored) {}
        return s;
    }
}