import java.sql.*;

/**
 * Resets all parking slots to AVAILABLE and clears stuck sessions.
 * Run with: mvn compile exec:java "-Dexec.mainClass=ResetSlots" -q
 */
public class ResetSlots {
    private static final String DB_URL = 
        "jdbc:postgresql://ep-fancy-cloud-a1r5u3td-pooler.ap-southeast-1.aws.neon.tech/neondb"
        + "?sslmode=require&connectTimeout=30&socketTimeout=30&tcpKeepAlive=true"
        + "&user=neondb_owner&password=npg_F1rbPhBe5GmZ";

    public static void main(String[] args) {
        System.out.println("=== SnapPark DB Reset Tool ===");
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            System.out.println("Connected!");
            Statement stmt = conn.createStatement();

            // Show current state
            ResultSet rs = stmt.executeQuery("SELECT status, COUNT(*) as cnt FROM parking_slots GROUP BY status ORDER BY status");
            System.out.println("\n--- Before Reset ---");
            while (rs.next()) {
                System.out.println("  " + rs.getString("status") + ": " + rs.getInt("cnt"));
            }

            // Show stuck sessions
            rs = stmt.executeQuery("SELECT COUNT(*) FROM parking_sessions WHERE active = 1");
            rs.next();
            int activeSessions = rs.getInt(1);
            System.out.println("  Active sessions: " + activeSessions);

            // Reset ALL slots to AVAILABLE
            int resetSlots = stmt.executeUpdate("UPDATE parking_slots SET status = 'AVAILABLE'");
            System.out.println("\nReset " + resetSlots + " slots to AVAILABLE");

            // End all active sessions
            int endedSessions = stmt.executeUpdate("UPDATE parking_sessions SET active = 0 WHERE active = 1");
            System.out.println("Ended " + endedSessions + " stuck sessions");

            // Verify
            rs = stmt.executeQuery("SELECT status, COUNT(*) as cnt FROM parking_slots GROUP BY status ORDER BY status");
            System.out.println("\n--- After Reset ---");
            while (rs.next()) {
                System.out.println("  " + rs.getString("status") + ": " + rs.getInt("cnt"));
            }
            System.out.println("\n=== All slots are now AVAILABLE! ===");

        } catch (SQLException e) {
            System.err.println("DB Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
