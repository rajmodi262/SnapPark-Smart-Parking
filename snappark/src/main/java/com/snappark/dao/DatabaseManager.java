package com.snappark.dao;

import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

public class DatabaseManager {
    private static DatabaseManager instance;

    // Converted provided postgresql:// link to jdbc:postgresql:// format
    private static final String DB_URL = "jdbc:postgresql://ep-fancy-cloud-a1r5u3td-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&user=neondb_owner&password=npg_F1rbPhBe5GmZ";

    private DatabaseManager() { initializeDatabase(); }

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void initializeDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            System.out.println("Connecting to Neon Cloud Database...");

            // PostgreSQL uses SERIAL instead of AUTOINCREMENT for auto-generating internal primary keys
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, name TEXT NOT NULL, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL, active INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE IF NOT EXISTS parking_slots (id SERIAL PRIMARY KEY, slot_number TEXT UNIQUE NOT NULL, floor INTEGER NOT NULL, type TEXT NOT NULL, status TEXT DEFAULT 'AVAILABLE')");
            stmt.execute("CREATE TABLE IF NOT EXISTS vehicles (id SERIAL PRIMARY KEY, license_plate TEXT UNIQUE NOT NULL, owner_name TEXT NOT NULL, vehicle_type TEXT NOT NULL, phone_number TEXT DEFAULT '')");
            stmt.execute("CREATE TABLE IF NOT EXISTS parking_sessions (id SERIAL PRIMARY KEY, vehicle_id INTEGER, slot_id INTEGER, user_id INTEGER, entry_time TEXT, exit_time TEXT, active INTEGER DEFAULT 1, phone_number TEXT DEFAULT '', session_pin TEXT, exit_pin TEXT, payment_status TEXT DEFAULT 'PENDING')");
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (id SERIAL PRIMARY KEY, session_id INTEGER, amount REAL, discount REAL DEFAULT 0, payment_time TEXT, receipt TEXT, payment_method TEXT DEFAULT 'CASH', fine_amount REAL DEFAULT 0, exit_pin TEXT, session_pin TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS audit_logs (id SERIAL PRIMARY KEY, user_id INTEGER, action TEXT, details TEXT, timestamp TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS fines (id SERIAL PRIMARY KEY, session_id INTEGER NOT NULL, fine_type TEXT NOT NULL, amount REAL NOT NULL, assigned_slot TEXT, actual_slot TEXT, reported_by INTEGER, reported_at TEXT, paid INTEGER DEFAULT 0, notes TEXT)");

            seedData(conn);
            System.out.println("Cloud Database initialized! (Connected to Neon)");
        } catch (SQLException e) { 
            System.err.println("DB error: " + e.getMessage()); 
        }
    }

    private void seedData(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
        rs.next();
        if (rs.getInt(1) > 0) return; // DB already seeded

        String hash = BCrypt.hashpw("admin123", BCrypt.gensalt());
        PreparedStatement ps = conn.prepareStatement("INSERT INTO users (name,username,password_hash,role) VALUES (?,?,?,?)");
        ps.setString(1,"Admin User"); ps.setString(2,"admin"); ps.setString(3,hash); ps.setString(4,"ADMIN");
        ps.executeUpdate();

        for (int floor=1; floor<=2; floor++) {
            for (int i=1; i<=15; i++) {
                String sn="F"+floor+"-"+String.format("%02d",i);
                String type=(i<=5)?"BIKE":(i<=12)?"CAR":"SUV";
                stmt.execute("INSERT INTO parking_slots (slot_number,floor,type) VALUES ('"+sn+"',"+floor+",'"+type+"')");
            }
        }
        System.out.println("Seed data inserted into cloud DB!");
    }
}