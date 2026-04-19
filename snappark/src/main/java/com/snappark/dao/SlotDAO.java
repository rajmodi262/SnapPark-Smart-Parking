package com.snappark.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.snappark.model.ParkingSlot;
import com.snappark.model.enums.SlotStatus;
import com.snappark.model.enums.VehicleType;

public class SlotDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public List<ParkingSlot> findAll() {
        List<ParkingSlot> slots = new ArrayList<>();
        String sql = "SELECT * FROM parking_slots ORDER BY floor, slot_number";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) slots.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error finding slots: " + e.getMessage());
        }
        return slots;
    }

    public List<ParkingSlot> findByFloor(int floor) {
        List<ParkingSlot> slots = new ArrayList<>();
        String sql = "SELECT * FROM parking_slots WHERE floor = ? ORDER BY slot_number";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, floor);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) slots.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("Error finding slots by floor: " + e.getMessage());
        }
        return slots;
    }

    public boolean updateStatus(int slotId, SlotStatus status) {
        String sql = "UPDATE parking_slots SET status = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, slotId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error updating slot status: " + e.getMessage());
            return false;
        }
    }

    public ParkingSlot findById(int id) {
        String sql = "SELECT * FROM parking_slots WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("Error finding slot: " + e.getMessage());
        }
        return null;
    }

    private ParkingSlot mapRow(ResultSet rs) throws SQLException {
        return new ParkingSlot(
            rs.getInt("id"),
            rs.getString("slot_number"),
            rs.getInt("floor"),
            VehicleType.valueOf(rs.getString("type")),
            SlotStatus.valueOf(rs.getString("status"))
        );
    }
}