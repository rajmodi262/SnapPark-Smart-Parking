package com.snappark.dao;

import java.sql.*;
import java.util.concurrent.LinkedBlockingQueue;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Database Manager with Connection Pooling for Neon Cloud DB.
 * 
 * The Neon free-tier DB is in Singapore — creating a new TCP connection
 * takes 5-20 seconds each time. This pool keeps connections alive and
 * reuses them, making queries instant after the first connection.
 */
public class DatabaseManager {
    private static DatabaseManager instance;

    // DB URL: reads from env var first, falls back to default for dev
    private static final String DEFAULT_DB_URL = "jdbc:postgresql://ep-fancy-cloud-a1r5u3td-pooler.ap-southeast-1.aws.neon.tech/neondb"
        + "?sslmode=require"
        + "&connectTimeout=30"
        + "&socketTimeout=60"
        + "&tcpKeepAlive=true"
        + "&user=neondb_owner"
        + "&password=npg_F1rbPhBe5GmZ";
    private static final String DB_URL = System.getenv("SNAPPARK_DB_URL") != null
        ? System.getenv("SNAPPARK_DB_URL") : DEFAULT_DB_URL;

    // Connection pool
    private static final int POOL_SIZE = 5;
    private final LinkedBlockingQueue<Connection> pool = new LinkedBlockingQueue<>(POOL_SIZE);
    private volatile boolean initialized = false;

    private DatabaseManager() {
        initializeDatabase();
    }

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    /**
     * Get a connection from the pool. If pool is empty, create a new one.
     * IMPORTANT: Callers MUST close the returned connection (which returns it to pool).
     */
    public Connection getConnection() throws SQLException {
        // Try to get a pooled connection
        Connection conn = pool.poll();
        if (conn != null) {
            // Verify it's still alive
            try {
                if (!conn.isClosed() && conn.isValid(2)) {
                    return new PooledConnection(conn, pool);
                }
            } catch (Exception e) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }

        // Create a new connection
        long start = System.currentTimeMillis();
        conn = DriverManager.getConnection(DB_URL);
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("[DB Pool] New connection created in " + elapsed + "ms");
        return new PooledConnection(conn, pool);
    }

    /**
     * Pre-warm the connection pool with ready-to-use connections.
     */
    private void warmPool() {
        System.out.println("[DB Pool] Warming up " + POOL_SIZE + " connections...");
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                Connection conn = DriverManager.getConnection(DB_URL);
                if (!pool.offer(conn)) {
                    conn.close(); // Pool full, discard
                }
            } catch (SQLException e) {
                System.err.println("[DB Pool] Warm-up connection " + (i+1) + " failed: " + e.getMessage());
                break; // Don't keep trying if network is flaky
            }
        }
        System.out.println("[DB Pool] Pool warmed with " + pool.size() + " connections");
    }

    // ─── Keep-alive thread ───────────────────────────────────────
    private void startKeepAlive() {
        Thread keepAlive = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60_000); // Every 60 seconds
                    // Ping one connection to keep the pool alive
                    Connection conn = pool.poll();
                    if (conn != null) {
                        try {
                            if (!conn.isClosed()) {
                                conn.createStatement().execute("SELECT 1");
                                pool.offer(conn);
                            }
                        } catch (Exception e) {
                            try { conn.close(); } catch (Exception ignored) {}
                            // Replace with fresh connection
                            try {
                                Connection fresh = DriverManager.getConnection(DB_URL);
                                pool.offer(fresh);
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        keepAlive.setDaemon(true);
        keepAlive.setName("DB-KeepAlive");
        keepAlive.start();
    }

    // ─── Initialization with retry ───────────────────────────────
    private void initializeDatabase() {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                long start = System.currentTimeMillis();
                Connection conn = DriverManager.getConnection(DB_URL);
                long elapsed = System.currentTimeMillis() - start;
                System.out.println("Connected to Neon Cloud Database in " + elapsed + "ms (attempt " + attempt + ")");

                Statement stmt = conn.createStatement();
                stmt.execute("CREATE TABLE IF NOT EXISTS users (id SERIAL PRIMARY KEY, name TEXT NOT NULL, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL, active INTEGER DEFAULT 1)");
                stmt.execute("CREATE TABLE IF NOT EXISTS parking_slots (id SERIAL PRIMARY KEY, slot_number TEXT UNIQUE NOT NULL, floor INTEGER NOT NULL, type TEXT NOT NULL, status TEXT DEFAULT 'AVAILABLE')");
                stmt.execute("CREATE TABLE IF NOT EXISTS vehicles (id SERIAL PRIMARY KEY, license_plate TEXT UNIQUE NOT NULL, owner_name TEXT NOT NULL, vehicle_type TEXT NOT NULL, phone_number TEXT DEFAULT '')");
                stmt.execute("CREATE TABLE IF NOT EXISTS parking_sessions (id SERIAL PRIMARY KEY, vehicle_id INTEGER, slot_id INTEGER, user_id INTEGER, entry_time TEXT, exit_time TEXT, active INTEGER DEFAULT 1, phone_number TEXT DEFAULT '', session_pin TEXT, exit_pin TEXT, payment_status TEXT DEFAULT 'PENDING')");
                stmt.execute("CREATE TABLE IF NOT EXISTS transactions (id SERIAL PRIMARY KEY, session_id INTEGER, amount REAL, discount REAL DEFAULT 0, payment_time TEXT, receipt TEXT, payment_method TEXT DEFAULT 'CASH', fine_amount REAL DEFAULT 0, exit_pin TEXT, session_pin TEXT)");
                stmt.execute("CREATE TABLE IF NOT EXISTS audit_logs (id SERIAL PRIMARY KEY, user_id INTEGER, action TEXT, details TEXT, timestamp TEXT)");
                stmt.execute("CREATE TABLE IF NOT EXISTS fines (id SERIAL PRIMARY KEY, session_id INTEGER NOT NULL, fine_type TEXT NOT NULL, amount REAL NOT NULL, assigned_slot TEXT, actual_slot TEXT, reported_by INTEGER, reported_at TEXT, paid INTEGER DEFAULT 0, notes TEXT)");

                seedData(conn);
                
                // Return first connection to pool
                pool.offer(conn);
                
                System.out.println("Cloud Database initialized! (Connected to Neon)");
                initialized = true;

                // Warm up remaining pool connections in background
                Thread warmThread = new Thread(() -> warmPool());
                warmThread.setDaemon(true);
                warmThread.start();

                // Start keep-alive pinger
                startKeepAlive();

                return; // Success
            } catch (SQLException e) {
                System.err.println("DB connection attempt " + attempt + "/" + maxRetries + " failed: " + e.getMessage());
                if (attempt < maxRetries) {
                    int waitSec = attempt * 5;
                    System.out.println("Neon DB may be waking up from sleep. Retrying in " + waitSec + "s...");
                    try { Thread.sleep(waitSec * 1000L); } catch (InterruptedException ignored) {}
                } else {
                    System.err.println("CRITICAL: Could not connect to database after " + maxRetries + " attempts!");
                }
            }
        }
    }

    public boolean isInitialized() { return initialized; }

    // ─── Seed Data ───────────────────────────────────────────────
    private void seedData(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rsUsers = stmt.executeQuery("SELECT COUNT(*) FROM users");
        rsUsers.next();
        if (rsUsers.getInt(1) == 0) {
            String hash = BCrypt.hashpw("admin123", BCrypt.gensalt());
            PreparedStatement ps = conn.prepareStatement("INSERT INTO users (name,username,password_hash,role) VALUES (?,?,?,?)");
            ps.setString(1,"Admin User"); ps.setString(2,"admin"); ps.setString(3,hash); ps.setString(4,"ADMIN");
            ps.executeUpdate();
            System.out.println("Users seeded into cloud DB!");
        }

        ResultSet rsSlots = stmt.executeQuery("SELECT COUNT(*) FROM parking_slots");
        rsSlots.next();
        int slotCount = rsSlots.getInt(1);
        // Expected: 1 floor × 15 slots (5 BIKE + 5 CAR + 5 SUV) = 15
        if (slotCount != 15) {
            // Clear old layout and re-seed
            stmt.execute("DELETE FROM parking_slots");
            for (int i=1; i<=15; i++) {
                String sn="F1-"+String.format("%02d",i);
                String type=(i<=5)?"BIKE":(i<=10)?"CAR":"SUV";
                stmt.execute("INSERT INTO parking_slots (slot_number,floor,type) VALUES ('"+sn+"',1,'"+type+"')");
            }
            System.out.println("Parking slots re-seeded: 5 BIKE + 5 CAR + 5 SUV = 15 slots!");
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  PooledConnection — returns to pool instead of closing
    // ═════════════════════════════════════════════════════════════
    private static class PooledConnection implements Connection {
        private final Connection real;
        private final LinkedBlockingQueue<Connection> pool;
        private boolean returned = false;

        PooledConnection(Connection real, LinkedBlockingQueue<Connection> pool) {
            this.real = real;
            this.pool = pool;
        }

        @Override
        public void close() throws SQLException {
            if (!returned && !real.isClosed()) {
                returned = true;
                if (!pool.offer(real)) {
                    real.close(); // Pool full, actually close
                }
            }
        }

        // Delegate all Connection methods to the real connection
        @Override public Statement createStatement() throws SQLException { return real.createStatement(); }
        @Override public PreparedStatement prepareStatement(String sql) throws SQLException { return real.prepareStatement(sql); }
        @Override public CallableStatement prepareCall(String sql) throws SQLException { return real.prepareCall(sql); }
        @Override public String nativeSQL(String sql) throws SQLException { return real.nativeSQL(sql); }
        @Override public void setAutoCommit(boolean autoCommit) throws SQLException { real.setAutoCommit(autoCommit); }
        @Override public boolean getAutoCommit() throws SQLException { return real.getAutoCommit(); }
        @Override public void commit() throws SQLException { real.commit(); }
        @Override public void rollback() throws SQLException { real.rollback(); }
        @Override public boolean isClosed() throws SQLException { return real.isClosed(); }
        @Override public DatabaseMetaData getMetaData() throws SQLException { return real.getMetaData(); }
        @Override public void setReadOnly(boolean readOnly) throws SQLException { real.setReadOnly(readOnly); }
        @Override public boolean isReadOnly() throws SQLException { return real.isReadOnly(); }
        @Override public void setCatalog(String catalog) throws SQLException { real.setCatalog(catalog); }
        @Override public String getCatalog() throws SQLException { return real.getCatalog(); }
        @Override public void setTransactionIsolation(int level) throws SQLException { real.setTransactionIsolation(level); }
        @Override public int getTransactionIsolation() throws SQLException { return real.getTransactionIsolation(); }
        @Override public SQLWarning getWarnings() throws SQLException { return real.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { real.clearWarnings(); }
        @Override public Statement createStatement(int a, int b) throws SQLException { return real.createStatement(a, b); }
        @Override public PreparedStatement prepareStatement(String s, int a, int b) throws SQLException { return real.prepareStatement(s, a, b); }
        @Override public CallableStatement prepareCall(String s, int a, int b) throws SQLException { return real.prepareCall(s, a, b); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return real.getTypeMap(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException { real.setTypeMap(map); }
        @Override public void setHoldability(int h) throws SQLException { real.setHoldability(h); }
        @Override public int getHoldability() throws SQLException { return real.getHoldability(); }
        @Override public Savepoint setSavepoint() throws SQLException { return real.setSavepoint(); }
        @Override public Savepoint setSavepoint(String name) throws SQLException { return real.setSavepoint(name); }
        @Override public void rollback(Savepoint sp) throws SQLException { real.rollback(sp); }
        @Override public void releaseSavepoint(Savepoint sp) throws SQLException { real.releaseSavepoint(sp); }
        @Override public Statement createStatement(int a, int b, int c) throws SQLException { return real.createStatement(a, b, c); }
        @Override public PreparedStatement prepareStatement(String s, int a, int b, int c) throws SQLException { return real.prepareStatement(s, a, b, c); }
        @Override public CallableStatement prepareCall(String s, int a, int b, int c) throws SQLException { return real.prepareCall(s, a, b, c); }
        @Override public PreparedStatement prepareStatement(String s, int k) throws SQLException { return real.prepareStatement(s, k); }
        @Override public PreparedStatement prepareStatement(String s, int[] i) throws SQLException { return real.prepareStatement(s, i); }
        @Override public PreparedStatement prepareStatement(String s, String[] c) throws SQLException { return real.prepareStatement(s, c); }
        @Override public Clob createClob() throws SQLException { return real.createClob(); }
        @Override public Blob createBlob() throws SQLException { return real.createBlob(); }
        @Override public NClob createNClob() throws SQLException { return real.createNClob(); }
        @Override public SQLXML createSQLXML() throws SQLException { return real.createSQLXML(); }
        @Override public boolean isValid(int timeout) throws SQLException { return real.isValid(timeout); }
        @Override public void setClientInfo(String k, String v) throws SQLClientInfoException { real.setClientInfo(k, v); }
        @Override public void setClientInfo(java.util.Properties p) throws SQLClientInfoException { real.setClientInfo(p); }
        @Override public String getClientInfo(String name) throws SQLException { return real.getClientInfo(name); }
        @Override public java.util.Properties getClientInfo() throws SQLException { return real.getClientInfo(); }
        @Override public Array createArrayOf(String t, Object[] e) throws SQLException { return real.createArrayOf(t, e); }
        @Override public Struct createStruct(String t, Object[] a) throws SQLException { return real.createStruct(t, a); }
        @Override public void setSchema(String schema) throws SQLException { real.setSchema(schema); }
        @Override public String getSchema() throws SQLException { return real.getSchema(); }
        @Override public void abort(java.util.concurrent.Executor executor) throws SQLException { real.abort(executor); }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor e, int ms) throws SQLException { real.setNetworkTimeout(e, ms); }
        @Override public int getNetworkTimeout() throws SQLException { return real.getNetworkTimeout(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return real.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return real.isWrapperFor(iface); }
    }
}