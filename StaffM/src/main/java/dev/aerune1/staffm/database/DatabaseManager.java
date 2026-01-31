package dev.aerune1.staffm.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.aerune1.staffm.ConfigManager;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {
    private final HikariDataSource dataSource;

    public DatabaseManager(ConfigManager config, Path dataDir) {
        HikariConfig hikariConfig = new HikariConfig();
        
        String type = config.getString("database.type");
        if ("mysql".equalsIgnoreCase(type)) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getString("database.host") + ":" + config.getLong("database.port") + "/" + config.getString("database.database"));
            hikariConfig.setUsername(config.getString("database.username"));
            hikariConfig.setPassword(config.getString("database.password"));
        } else {
            // Default to H2 (File-based)
            // .toAbsolutePath().toString() ensures H2 finds the path correctly
            hikariConfig.setJdbcUrl("jdbc:h2:" + dataDir.resolve("staffm_data").toAbsolutePath().toString() + ";MODE=MySQL");
            hikariConfig.setDriverClassName("org.h2.Driver");
        }

        // Optimization settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.setMaximumPoolSize(10); 

        this.dataSource = new HikariDataSource(hikariConfig);
        initTables();
    }

    private void initTables() {
        try (Connection conn = dataSource.getConnection()) {
            // Table for raw sessions
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS staff_sessions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36), " +
                "start_time BIGINT, " +
                "end_time BIGINT, " +
                "afk_time BIGINT DEFAULT 0" +
                ")"
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    // Async Method to log a session
    public CompletableFuture<Void> logSession(UUID uuid, long start, long end, long afkMillis) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO staff_sessions (uuid, start_time, end_time, afk_time) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, start);
                ps.setLong(3, end);
                ps.setLong(4, afkMillis);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    // Async Method to get stats for a specific time window
    public CompletableFuture<Long> getPlaytime(UUID uuid, long sinceTimestamp) {
        return CompletableFuture.supplyAsync(() -> {
            long totalPlaytime = 0;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                     "SELECT start_time, end_time, afk_time FROM staff_sessions WHERE uuid = ? AND end_time > ?")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, sinceTimestamp);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long start = rs.getLong("start_time");
                        // If the session started before our window, clamp it to the window start
                        long effectiveStart = Math.max(start, sinceTimestamp);
                        
                        long end = rs.getLong("end_time");
                        long afk = rs.getLong("afk_time");
                        
                        long sessionDuration = end - effectiveStart;
                        
                        // NOTE: This basic math assumes AFK happened uniformly. 
                        // For ultra-precision, you'd need to log every AFK event as a separate DB row.
                        // But for quota tracking, this is standard and sufficient.
                        if (sessionDuration > 0) {
                            totalPlaytime += Math.max(0, sessionDuration - afk);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return totalPlaytime; // Returns milliseconds
        });
    }
}