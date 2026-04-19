package com.snappark.service;

import com.snappark.dao.DatabaseManager;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class AnalyticsService {
    private static AnalyticsService instance;
    private final DatabaseManager db = DatabaseManager.getInstance();

    private AnalyticsService() {}

    public static AnalyticsService getInstance() {
        if (instance == null) instance = new AnalyticsService();
        return instance;
    }

    public double getTodayRevenue() {
        String today = LocalDate.now().toString();
        String sql = "SELECT COALESCE(SUM(amount - discount), 0) FROM transactions WHERE payment_time LIKE ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, today + "%");
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return 0;
    }

    public double getTotalRevenue() {
        String sql = "SELECT COALESCE(SUM(amount - discount), 0) FROM transactions";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return 0;
    }

    public int getTotalVehicles() {
        String sql = "SELECT COUNT(*) FROM vehicles";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return 0;
    }

    public Map<String, Double> getRevenueByVehicleType() {
        Map<String, Double> map = new HashMap<>();
        map.put("BIKE", 0.0); map.put("CAR", 0.0); map.put("SUV", 0.0);
        String sql = "SELECT v.vehicle_type, COALESCE(SUM(t.amount - t.discount), 0) as rev " +
                     "FROM transactions t JOIN parking_sessions s ON t.session_id = s.id " +
                     "JOIN vehicles v ON s.vehicle_id = v.id GROUP BY v.vehicle_type";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) map.put(rs.getString("vehicle_type"), rs.getDouble("rev"));
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return map;
    }

    public Map<String, Integer> getSlotUtilization() {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT status, COUNT(*) as cnt FROM parking_slots GROUP BY status";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) map.put(rs.getString("status"), rs.getInt("cnt"));
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return map;
    }

    public int getTotalSessions() {
        String sql = "SELECT COUNT(*) FROM parking_sessions";
        try (Connection conn = db.getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return 0;
    }
}