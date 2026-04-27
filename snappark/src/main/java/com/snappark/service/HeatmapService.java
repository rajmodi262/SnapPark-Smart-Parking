package com.snappark.service;

import com.snappark.dao.DatabaseManager;
import javafx.scene.paint.Color;
import java.sql.*;
import java.util.*;

public class HeatmapService {

    private static HeatmapService instance;
    private final DatabaseManager db = DatabaseManager.getInstance();

    private HeatmapService() {}

    public static synchronized HeatmapService getInstance() {
        if (instance == null) instance = new HeatmapService();
        return instance;
    }

    // Returns map of slotId -> total booking count (all time)
    public Map<Integer, Integer> getAllTimeBookings() {
        Map<Integer, Integer> counts = new HashMap<>();
        String sql = "SELECT slot_id, COUNT(*) as cnt FROM parking_sessions GROUP BY slot_id";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next())
                counts.put(rs.getInt("slot_id"), rs.getInt("cnt"));
        } catch (SQLException e) { System.err.println("Heatmap error: " + e.getMessage()); }
        return counts;
    }

    // Returns map of slotId -> bookings in last N days
    public Map<Integer, Integer> getRecentBookings(int days) {
        Map<Integer, Integer> counts = new HashMap<>();
        String sql = "SELECT slot_id, COUNT(*) as cnt FROM parking_sessions " +
                     "WHERE entry_time >= (NOW() - INTERVAL '" + days + " days')::TEXT GROUP BY slot_id";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next())
                counts.put(rs.getInt("slot_id"), rs.getInt("cnt"));
        } catch (SQLException e) { System.err.println("Heatmap error: " + e.getMessage()); }
        return counts;
    }

    // Returns the max booking count across all slots (for normalization)
    public int getMaxCount(Map<Integer, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    // Interpolates color: 0 = cool blue, 0.5 = amber, 1.0 = hot red
    // Returns a semi-transparent color to overlay on the slot
    public Color getHeatColor(int count, int max) {
        if (count == 0 || max == 0) return Color.TRANSPARENT;
        double t = Math.min(1.0, (double) count / max);

        double r, g, b, a;
        if (t < 0.25) {
            // cold: deep blue to cyan
            double s = t / 0.25;
            r = 0.0;
            g = s * 0.6;
            b = 1.0;
            a = 0.20 + s * 0.10;
        } else if (t < 0.5) {
            // cool: cyan to yellow-green
            double s = (t - 0.25) / 0.25;
            r = s * 0.8;
            g = 0.6 + s * 0.4;
            b = 1.0 - s;
            a = 0.28 + s * 0.10;
        } else if (t < 0.75) {
            // warm: yellow to orange
            double s = (t - 0.5) / 0.25;
            r = 1.0;
            g = 1.0 - s * 0.4;
            b = 0.0;
            a = 0.35 + s * 0.10;
        } else {
            // hot: orange to blazing red
            double s = (t - 0.75) / 0.25;
            r = 1.0;
            g = 0.6 - s * 0.6;
            b = 0.0;
            a = 0.45 + s * 0.20;
        }
        return Color.color(
            Math.min(1, r),
            Math.min(1, g),
            Math.min(1, b),
            Math.min(0.72, a)
        );
    }

    // Returns a label string like "5x" or "HOT" for the slot badge
    public String getHeatLabel(int count) {
        if (count == 0)   return "";
        if (count >= 10)  return "HOT";
        if (count >= 5)   return count + "x";
        return count + "x";
    }

    // Returns neon glow color for border of hottest slots
    public Color getGlowColor(int count, int max) {
        if (max == 0 || count == 0) return Color.TRANSPARENT;
        double t = Math.min(1.0, (double) count / max);
        if (t >= 0.8) return Color.web("#FF2200", 0.9);
        if (t >= 0.5) return Color.web("#FF8800", 0.7);
        if (t >= 0.25) return Color.web("#FFDD00", 0.5);
        return Color.web("#0088FF", 0.4);
    }
}