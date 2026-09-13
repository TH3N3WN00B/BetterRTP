package me.SuperRonanCraft.BetterRTP.references.database;

import me.SuperRonanCraft.BetterRTP.BetterRTP;
import me.SuperRonanCraft.BetterRTP.versions.AsyncHandler;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class DatabaseChunkData extends SQLite {

    /** Hard cap on cached entries so the in-memory map stays a few MB at most. */
    private static final int MAX_CACHED_CHUNKS = 50_000;

    //In-memory cache: world:x:z -> last known data for that chunk
    //Ordered by last access so the oldest entries are evicted first.
    private final Map<String, ChunkDataInfo> cached = Collections.synchronizedMap(
            new LinkedHashMap<String, ChunkDataInfo>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ChunkDataInfo> eldest) {
                    return size() > MAX_CACHED_CHUNKS;
                }
            });

    public DatabaseChunkData() {
        super(DATABASE_TYPE.CHUNK_DATA);
    }

    @Override
    public List<String> getTables() {
        List<String> list = new ArrayList<>();
        list.add("ChunkData");
        return list;
    }

    public enum COLUMNS {
        ID("id", "integer PRIMARY KEY AUTOINCREMENT"),
        //Chunk Data
        WORLD("world", "varchar(32)"),
        X("x", "long"),
        Z("z", "long"),
        BIOME("biome", "string"),
        MAX_Y("max_y", "integer"),
        ;

        public final String name;
        public final String type;

        COLUMNS(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    @Override
    protected String getCreateTable(String table) {
        //Enforce one row per (world, x, z)
        String str = super.getCreateTable(table);
        return str.substring(0, str.length() - 1) + ", UNIQUE (" + COLUMNS.WORLD.name + ", " + COLUMNS.X.name + ", "
                + COLUMNS.Z.name + "))";
    }

    @Override
    protected void afterCreate(Connection connection) {
        try (Statement st = connection.createStatement()) {
            //Remove duplicate rows left over from versions without the UNIQUE constraint
            st.executeUpdate("DELETE FROM `" + tables.get(0) + "` WHERE `" + COLUMNS.ID.name + "` NOT IN (SELECT MIN(`"
                    + COLUMNS.ID.name + "`) FROM `" + tables.get(0) + "` GROUP BY `" + COLUMNS.WORLD.name + "`, `"
                    + COLUMNS.X.name + "`, `" + COLUMNS.Z.name + "`)");
            //Fast range lookups (world first, then x, then z)
            st.executeUpdate("CREATE INDEX IF NOT EXISTS `chunk_data_xyz` ON `" + tables.get(0) + "` (`"
                    + COLUMNS.WORLD.name + "`, `" + COLUMNS.X.name + "`, `" + COLUMNS.Z.name + "`)");
        } catch (SQLException e) {
            BetterRTP.getInstance().getLogger().log(Level.WARNING, "Could not clean up previous chunk data", e);
        }
    }

    public void addChunk(Chunk chunk, int maxy, Biome biome) {
        if (chunk == null)
            return;
        addChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), biome == null ? "" : biome.name(), maxy);
    }

    public void addChunk(String world, int x, int z, String biome, int maxy) {
        ChunkDataInfo info = new ChunkDataInfo(world, x, z, biome, maxy);
        cached.put(key(world, x, z), info);
        AsyncHandler.async(() -> insert(Collections.singletonList(info)));
    }

    /**
     * Saves many chunks at once inside a single transaction. Used by the pre-loader in RandomLocation.runChunkTest.
     */
    public void addChunks(List<ChunkDataInfo> infos) {
        if (infos == null || infos.isEmpty())
            return;
        for (ChunkDataInfo info : infos)
            cached.put(key(info.world, info.x, info.z), info);
        AsyncHandler.async(() -> insert(infos));
    }

    /**
     * Fast lookup of the last known data for a chunk. Returns null when the chunk was never cached.
     */
    public ChunkDataInfo getCached(String world, int x, int z) {
        return cached.get(key(world, x, z));
    }

    public void clearCache() {
        cached.clear();
    }

    private void insert(List<ChunkDataInfo> infos) {
        if (infos == null || infos.isEmpty())
            return;
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getSQLConnection();
            conn.setAutoCommit(false);
            ps = conn.prepareStatement("INSERT OR REPLACE INTO " + tables.get(0) + " ("
                    + COLUMNS.WORLD.name + ", "
                    + COLUMNS.X.name + ", "
                    + COLUMNS.Z.name + ", "
                    + COLUMNS.BIOME.name + ", "
                    + COLUMNS.MAX_Y.name + " "
                    + ") VALUES(?, ?, ?, ?, ?)");
            for (ChunkDataInfo info : infos) {
                ps.setString(1, info.world);
                ps.setLong(2, info.x);
                ps.setLong(3, info.z);
                ps.setString(4, info.biome);
                ps.setInt(5, info.maxY);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException ex) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (SQLException ignored) {
            }
            BetterRTP.getInstance().getLogger().log(Level.SEVERE, Errors.sqlConnectionExecute(), ex);
        } finally {
            try {
                if (ps != null)
                    ps.close();
            } catch (SQLException ignored) {
            }
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
            SQLiteConnector.release(conn);
        }
    }

    private static String key(String world, long x, long z) {
        return world + ":" + x + ":" + z;
    }

    public static class ChunkDataInfo {

        public final String world;
        public final long x;
        public final long z;
        public final String biome;
        public final int maxY;

        public ChunkDataInfo(String world, long x, long z, String biome, int maxY) {
            this.world = world;
            this.x = x;
            this.z = z;
            this.biome = biome == null ? "" : biome;
            this.maxY = maxY;
        }

        /**
         * True when this biome is inside the allowed list (the list being empty/null allows everything).
         */
        public boolean isBiomeAllowed(List<String> allowedBiomes) {
            if (allowedBiomes == null || allowedBiomes.isEmpty() || biome.isEmpty())
                return true;
            for (String allowed : allowedBiomes)
                if (allowed != null && !allowed.isEmpty() && biome.toUpperCase().contains(allowed.toUpperCase()))
                    return true;
            return false;
        }
    }
}