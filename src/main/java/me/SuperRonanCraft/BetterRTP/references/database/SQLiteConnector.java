package me.SuperRonanCraft.BetterRTP.references.database;

import me.SuperRonanCraft.BetterRTP.BetterRTP;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Small connection pool for the SQLite database.
 * Each connection is configured with WAL mode, a busy timeout and synchronous=NORMAL
 * so concurrent readers/writers on the same database file behave well.
 */
public final class SQLiteConnector {

    private static final int MAX_POOL_SIZE = 5;
    private static final Deque<Connection> pool = new ArrayDeque<>();
    private static final AtomicInteger connectionsCreated = new AtomicInteger();
    private static File databaseFile;
    private static boolean driverLoaded;

    private SQLiteConnector() {
    }

    public static Connection getConnection() {
        Connection conn = pool.poll();
        if (conn != null) {
            try {
                if (!conn.isClosed())
                    return conn;
            } catch (SQLException ignored) {
            }
        }
        if (connectionsCreated.get() < MAX_POOL_SIZE) {
            Connection newConn = openConnection();
            if (newConn != null)
                connectionsCreated.incrementAndGet();
            return newConn;
        }
        // Cap reached and pool empty: open a transient connection instead of growing forever
        return openConnection();
    }

    private static Connection openConnection() {
        loadDriver();
        File file = getDatabaseFile();
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
                st.execute("PRAGMA synchronous=NORMAL");
            }
            return conn;
        } catch (SQLException ex) {
            BetterRTP.getInstance().getLogger().log(Level.SEVERE, "SQLite exception on initialize", ex);
        }
        return null;
    }

    private static void loadDriver() {
        if (driverLoaded)
            return;
        try {
            Class.forName("org.sqlite.JDBC");
            driverLoaded = true;
        } catch (ClassNotFoundException ex) {
            BetterRTP.getInstance().getLogger().log(Level.SEVERE, "You need the SQLite JBDC library. Google it Ronan...");
        }
    }

    private static File getDatabaseFile() {
        if (databaseFile == null) {
            File dataFolder = BetterRTP.getInstance().getDataFolder();
            databaseFile = new File(dataFolder, "data" + File.separator + "database.db");
            if (!databaseFile.exists()) {
                try {
                    File parent = databaseFile.getParentFile();
                    if (parent != null && !parent.exists())
                        parent.mkdirs();
                    databaseFile.createNewFile();
                } catch (IOException e) {
                    BetterRTP.getInstance().getLogger().log(Level.SEVERE, "File write error: " + databaseFile.getPath(), e);
                }
            }
        }
        return databaseFile;
    }

    /**
     * Returns a connection to the pool for reuse. Connections with an open transaction
     * are closed instead so pooled connections are always clean.
     */
    public static void release(Connection conn) {
        if (conn == null)
            return;
        try {
            if (!conn.isClosed() && conn.getAutoCommit())
                pool.offer(conn);
            else
                conn.close();
        } catch (SQLException ex) {
            closeQuietly(conn);
        }
    }

    public static synchronized void shutdown() {
        while (!pool.isEmpty())
            closeQuietly(pool.poll());
        connectionsCreated.set(0);
    }

    private static void closeQuietly(Connection conn) {
        try {
            if (conn != null)
                conn.close();
        } catch (SQLException ignored) {
        }
    }
}