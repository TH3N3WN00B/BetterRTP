package me.SuperRonanCraft.BetterRTP.references.database;

import lombok.NonNull;
import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

public abstract class SQLite {

    @NonNull
    private final DATABASE_TYPE type;
    List<String> tables;
    private boolean loaded;

    public String addMissingColumns = "ALTER TABLE %table% ADD COLUMN %column% %type%";

    public SQLite(DATABASE_TYPE type) {
        this.type = type;
    }

    public abstract List<String> getTables();

    // SQL creation stuff
    public Connection getSQLConnection() {
        return SQLiteConnector.getConnection();
    }

    public void load() {
        loaded = false;
        tables = getTables();

        // Don't do anything if no columns to generate
        if (tables.isEmpty()) {
            loaded = true;
            return;
        }

        AsyncHandler.async(() -> {
            Connection connection = getSQLConnection();
            try {
                Statement s = connection.createStatement();
                for (String table : tables) {
                    s.executeUpdate(getCreateTable(table));
                    for (Enum<?> c : getColumns(type)) { //Add missing columns dynamically
                        try {
                            String _name = getColumnName(type, c);
                            String _type = getColumnType(type, c);
                            s.executeUpdate(addMissingColumns.replace("%table%", table).replace("%column%", _name).replace("%type%", _type));
                        } catch (SQLException ignored) {
                            //Column already exists
                        }
                    }
                    BetterRTP.debug("Database " + type.name() + ":" + table + " configured and loaded!");
                }
                s.close();
                //Subclasses may run migrations/cleanups right after the tables exist
                afterCreate(connection);
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                close(null, null, connection);
            }
            initialize();
            loaded = true;
        });
    }

    /**
     * This is the last process to run after creation when the table schema is up to date.
     */
    protected void afterCreate(Connection connection) {
    }

    //Force a UNIQUE constraint on tables that need one (append BEFORE the closing parenthesis)
    protected String getCreateTable(String table) {
        String str = "CREATE TABLE IF NOT EXISTS `" + table + "` (";
        Enum<?>[] columns = getColumns(type);
        for (Enum<?> c : columns) {
            String _name = getColumnName(type, c);
            String _type = getColumnType(type, c);
            str = str.concat("`" + _name + "` " + _type);
            if (c.equals(columns[columns.length - 1]))
                str = str.concat(")");
            else
                str = str.concat(", ");
        }
        return str;
    }

    private Enum<?>[] getColumns(DATABASE_TYPE type) {
        switch (type) {
            case CHUNK_DATA: return DatabaseChunkData.COLUMNS.values();
            case PLAYERS: return DatabasePlayers.COLUMNS.values();
            case QUEUE: return DatabaseQueue.COLUMNS.values();
            case COOLDOWN:
            default: return DatabaseCooldowns.COLUMNS.values();
        }
    }

    private String getColumnName(DATABASE_TYPE type, Enum<?> column) {
        switch (type) {
            case CHUNK_DATA: return ((DatabaseChunkData.COLUMNS) column).name;
            case PLAYERS: return ((DatabasePlayers.COLUMNS) column).name;
            case QUEUE: return ((DatabaseQueue.COLUMNS) column).name;
            case COOLDOWN:
            default: return ((DatabaseCooldowns.COLUMNS) column).name;
        }
    }

    private String getColumnType(DATABASE_TYPE type, Enum<?> column) {
        switch (type) {
            case CHUNK_DATA: return ((DatabaseChunkData.COLUMNS) column).type;
            case PLAYERS: return ((DatabasePlayers.COLUMNS) column).type;
            case QUEUE: return ((DatabaseQueue.COLUMNS) column).type;
            case COOLDOWN:
            default: return ((DatabaseCooldowns.COLUMNS) column).type;
        }
    }

    //Processing
    protected boolean sqlUpdate(String statement, @NonNull List<Object> params) {
        Connection conn = null;
        PreparedStatement ps = null;
        boolean success = true;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement(statement);
            Iterator<Object> it = params.iterator();
            int paramIndex = 1;
            while (it.hasNext()) {
                ps.setObject(paramIndex, it.next());
                paramIndex++;
            }
            ps.executeUpdate();
        } catch (SQLException ex) {
            BetterRTP.getInstance().getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
            success = false;
        } finally {
            close(ps, null, conn);
        }
        return success;
    }

    public void initialize() { //Let in console know if its all setup or not
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getSQLConnection();
            ps = conn.prepareStatement("SELECT * FROM " + tables.get(0) + " WHERE " + getColumnName(type, getColumns(type)[0]) + " = 0");

            rs = ps.executeQuery();
        } catch (SQLException ex) {
            BetterRTP.getInstance().getLogger().log(Level.SEVERE, "Unable to retrieve connection", ex);
        } finally {
            close(ps, rs, conn);
        }
    }

    protected void close(PreparedStatement ps, ResultSet rs, Connection conn) {
        try {
            if (ps != null) ps.close();
            if (rs != null) rs.close();
        } catch (SQLException ex) {
            Error.close(BetterRTP.getInstance(), ex);
        }
        //Return the connection to the pool instead of closing it
        SQLiteConnector.release(conn);
    }

    public boolean isLoaded() {
        return loaded;
    }

    public enum DATABASE_TYPE {
        PLAYERS,
        COOLDOWN,
        QUEUE,
        CHUNK_DATA,
    }
}