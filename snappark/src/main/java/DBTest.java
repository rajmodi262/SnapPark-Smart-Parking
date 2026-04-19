import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBTest {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://ep-fancy-cloud-a1r5u3td-pooler.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&user=neondb_owner&password=npg_F1rbPhBe5GmZ";
        System.out.println("Attempting to connect to: " + url.replaceAll("password=.*", "password=***"));
        try {
            long start = System.currentTimeMillis();
            Connection conn = DriverManager.getConnection(url);
            long end = System.currentTimeMillis();
            System.out.println("Success! Connected in " + (end - start) + "ms");
            conn.close();
            System.out.println("Connection closed safely.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
