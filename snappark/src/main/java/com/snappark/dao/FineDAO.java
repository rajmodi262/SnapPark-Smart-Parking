package com.snappark.dao;

import com.snappark.model.Fine;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FineDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public Fine save(Fine f) {
        String sql = "INSERT INTO fines (session_id, fine_type, amount, assigned_slot, actual_slot, " +
                     "reported_by, reported_at, paid, notes) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, f.getSessionId());
            ps.setString(2, f.getFineType().name());
            ps.setDouble(3, f.getAmount());
            ps.setString(4, f.getAssignedSlot());
            ps.setString(5, f.getActualSlot());
            ps.setInt(6, f.getReportedBy());
            ps.setString(7, f.getReportedAt() != null ? f.getReportedAt().toString() : LocalDateTime.now().toString());
            ps.setInt(8, f.isPaid() ? 1 : 0);
            ps.setString(9, f.getNotes());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) f.setId(keys.getInt(1));
        } catch (SQLException e) { System.err.println("FineDAO.save: " + e.getMessage()); }
        return f;
    }

    public List<Fine> findBySession(int sessionId) {
        List<Fine> list = new ArrayList<>();
        String sql = "SELECT * FROM fines WHERE session_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { System.err.println("FineDAO.findBySession: " + e.getMessage()); }
        return list;
    }

    public List<Fine> findUnpaidBySession(int sessionId) {
        List<Fine> list = new ArrayList<>();
        String sql = "SELECT * FROM fines WHERE session_id = ? AND paid = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { System.err.println("FineDAO.findUnpaid: " + e.getMessage()); }
        return list;
    }

    public double getUnpaidTotal(int sessionId) {
        String sql = "SELECT COALESCE(SUM(amount), 0) FROM fines WHERE session_id = ? AND paid = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);
        } catch (SQLException e) { System.err.println("FineDAO.getUnpaidTotal: " + e.getMessage()); }
        return 0;
    }

    public void markPaid(int sessionId) {
        String sql = "UPDATE fines SET paid = 1 WHERE session_id = ? AND paid = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) { System.err.println("FineDAO.markPaid: " + e.getMessage()); }
    }

    public List<Fine> findAll() {
        List<Fine> list = new ArrayList<>();
        String sql = "SELECT * FROM fines ORDER BY reported_at DESC";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { System.err.println("FineDAO.findAll: " + e.getMessage()); }
        return list;
    }

    private Fine mapRow(ResultSet rs) throws SQLException {
        Fine f = new Fine();
        f.setId(rs.getInt("id"));
        f.setSessionId(rs.getInt("session_id"));
        f.setFineType(Fine.FineType.valueOf(rs.getString("fine_type")));
        f.setAmount(rs.getDouble("amount"));
        f.setAssignedSlot(rs.getString("assigned_slot"));
        f.setActualSlot(rs.getString("actual_slot"));
        f.setReportedBy(rs.getInt("reported_by"));
        String at = rs.getString("reported_at");
        if (at != null && !at.isBlank()) f.setReportedAt(LocalDateTime.parse(at));
        f.setPaid(rs.getInt("paid") == 1);
        f.setNotes(rs.getString("notes"));
        return f;
    }
}
