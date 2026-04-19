package com.snappark.dao;
import com.snappark.model.Vehicle;
import com.snappark.model.enums.VehicleType;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VehicleDAO {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public Vehicle findByPlate(String plate) {
        String sql = "SELECT * FROM vehicles WHERE license_plate = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, plate.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return null;
    }

    public Vehicle save(Vehicle v) {
        String sql = "INSERT INTO vehicles " +
                     "(license_plate, owner_name, vehicle_type, phone_number) VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (license_plate) DO NOTHING";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, v.getLicensePlate().toUpperCase());
            ps.setString(2, v.getOwnerName());
            ps.setString(3, v.getVehicleType().name());
            ps.setString(4, v.getPhoneNumber());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) v.setId(keys.getInt(1));
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        // Always return fresh from DB so phone is set even for existing plates
        Vehicle fresh = findByPlate(v.getLicensePlate());
        // Update phone if it was empty
        if (fresh != null && (fresh.getPhoneNumber() == null || fresh.getPhoneNumber().isBlank())
                && v.getPhoneNumber() != null && !v.getPhoneNumber().isBlank()) {
            updatePhone(fresh.getId(), v.getPhoneNumber());
            fresh.setPhoneNumber(v.getPhoneNumber());
        }
        return fresh != null ? fresh : v;
    }

    public boolean updatePhone(int vehicleId, String phone) {
        String sql = "UPDATE vehicles SET phone_number = ? WHERE id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone); ps.setInt(2, vehicleId);
            ps.executeUpdate(); return true;
        } catch (SQLException e) { System.err.println(e.getMessage()); return false; }
    }

    public List<Vehicle> findAll() {
        List<Vehicle> list = new ArrayList<>();
        try (Connection c = db.getConnection(); Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM vehicles")) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) { System.err.println(e.getMessage()); }
        return list;
    }

    private Vehicle mapRow(ResultSet rs) throws SQLException {
        String phone = "";
        try { phone = rs.getString("phone_number"); } catch (Exception ignored) {}
        return new Vehicle(rs.getInt("id"), rs.getString("license_plate"),
            rs.getString("owner_name"), VehicleType.valueOf(rs.getString("vehicle_type")),
            phone != null ? phone : "");
    }
}