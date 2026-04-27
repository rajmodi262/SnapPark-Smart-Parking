package com.snappark.dao;

import com.snappark.model.VehicleFrequencyRow;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for generating report-level queries.
 * Joins vehicles + parking_sessions to produce monthly frequency data.
 */
public class ReportDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    /**
     * Get monthly vehicle visit frequency for a given year and month.
     * Groups by vehicle, counts sessions where entry_time falls in the selected month.
     * Sorted by frequency DESC, then license_plate ASC.
     *
     * @param year  4-digit year (e.g. 2026)
     * @param month 1-12
     * @return List of VehicleFrequencyRow, sorted by frequency desc
     */
    public List<VehicleFrequencyRow> getMonthlyFrequency(int year, int month) {
        List<VehicleFrequencyRow> rows = new ArrayList<>();
        // Build month prefix for LIKE matching (entry_time is stored as TEXT ISO format)
        String monthPrefix = String.format("%04d-%02d", year, month);

        String sql = "SELECT v.license_plate, v.phone_number, v.vehicle_type, COUNT(ps.id) AS freq " +
                     "FROM parking_sessions ps " +
                     "JOIN vehicles v ON v.id = ps.vehicle_id " +
                     "WHERE ps.entry_time LIKE ? " +
                     "GROUP BY v.id, v.license_plate, v.phone_number, v.vehicle_type " +
                     "ORDER BY freq DESC, v.license_plate ASC";

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, monthPrefix + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rows.add(new VehicleFrequencyRow(
                    rs.getString("license_plate"),
                    rs.getString("phone_number"),
                    rs.getString("vehicle_type"),
                    rs.getInt("freq")
                ));
            }
        } catch (SQLException e) {
            System.err.println("ReportDAO.getMonthlyFrequency error: " + e.getMessage());
        }
        return rows;
    }
}
