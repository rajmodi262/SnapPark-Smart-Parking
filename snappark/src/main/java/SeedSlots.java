import java.sql.*;

/**
 * Quick standalone script to check and seed parking_slots in the Neon Cloud DB.
 * Run with: mvn compile exec:java -Dexec.mainClass="SeedSlots" -q
 */
public class SeedSlots {
    private static final String DB_URL = 
        "jdbc:postgresql://ep-fancy-cloud-a1r5u3td-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&connectTimeout=30&socketTimeout=30&user=neondb_owner&password=npg_F1rbPhBe5GmZ";

    public static void main(String[] args) {
        System.out.println("=== SnapPark DB Slot Seeder ===");
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            System.out.println("Connected to Neon Cloud DB!");

            // Check current slot count
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM parking_slots");
            rs.next();
            int currentCount = rs.getInt(1);
            System.out.println("Current parking_slots count: " + currentCount);

            // Check statuses
            rs = stmt.executeQuery("SELECT status, COUNT(*) as cnt FROM parking_slots GROUP BY status");
            while (rs.next()) {
                System.out.println("  Status " + rs.getString("status") + ": " + rs.getInt("cnt") + " slots");
            }

            if (currentCount == 0) {
                System.out.println("\nNo slots found! Seeding 30 slots (2 floors x 15 slots)...");
                for (int floor = 1; floor <= 2; floor++) {
                    for (int i = 1; i <= 15; i++) {
                        String sn = "F" + floor + "-" + String.format("%02d", i);
                        String type = (i <= 5) ? "BIKE" : (i <= 12) ? "CAR" : "SUV";
                        stmt.execute("INSERT INTO parking_slots (slot_number, floor, type, status) VALUES ('" + sn + "', " + floor + ", '" + type + "', 'AVAILABLE')");
                    }
                }
                System.out.println("Successfully seeded 30 parking slots!");
            } else {
                System.out.println("\nSlots already exist. Checking for stuck LOCKING slots...");
                // Reset any stuck LOCKING slots to AVAILABLE
                int reset = stmt.executeUpdate("UPDATE parking_slots SET status = 'AVAILABLE' WHERE status = 'LOCKING'");
                if (reset > 0) {
                    System.out.println("Reset " + reset + " stuck LOCKING slots to AVAILABLE!");
                }
            }

            // Final count
            rs = stmt.executeQuery("SELECT status, COUNT(*) as cnt FROM parking_slots GROUP BY status ORDER BY status");
            System.out.println("\n=== Final Slot Status ===");
            while (rs.next()) {
                System.out.println("  " + rs.getString("status") + ": " + rs.getInt("cnt"));
            }
            System.out.println("=========================");

        } catch (SQLException e) {
            System.err.println("DB Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
