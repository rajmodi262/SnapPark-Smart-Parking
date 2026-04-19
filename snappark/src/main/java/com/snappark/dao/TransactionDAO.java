package com.snappark.dao;
import com.snappark.model.Transaction;
import java.sql.*;

public class TransactionDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public boolean save(Transaction t) {
        String sql = "INSERT INTO transactions " +
                     "(session_id, amount, discount, payment_time, receipt, payment_method, fine_amount, exit_pin, session_pin) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1,    t.getSessionId());
            ps.setDouble(2, t.getAmount());
            ps.setDouble(3, t.getDiscount());
            ps.setString(4, t.getPaymentTime() != null ? t.getPaymentTime().toString() : "");
            ps.setString(5, t.getReceipt());
            ps.setString(6, t.getPaymentMethod());
            ps.setDouble(7, t.getFineAmount());
            ps.setString(8, t.getExitPin());
            ps.setString(9, t.getSessionPin());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("TransactionDAO.save error: " + e.getMessage());
            return false;
        }
    }
}