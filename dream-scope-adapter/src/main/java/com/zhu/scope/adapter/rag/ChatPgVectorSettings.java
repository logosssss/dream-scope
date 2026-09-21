package com.zhu.scope.adapter.rag;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostgreSQL + pgvector 连接。web 从 {@code dream-scope.rag.pg.*} 填入。
 *
 * @see io.agentscope.core.rag.store.PgVectorStore
 */
public record ChatPgVectorSettings(
        String jdbcUrl, String username, String password, String schema, String tableName) {

    public static final String DEFAULT_SCHEMA = "public";

    public static final String DEFAULT_TABLE = "dream_scope_rag";

    private static final Logger log = LoggerFactory.getLogger(ChatPgVectorSettings.class);

    private static final Pattern DB_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public ChatPgVectorSettings {
        jdbcUrl = blankToNull(jdbcUrl);
        username = blankToDefault(username, "postgres");
        password = password == null ? "" : password;
        schema = blankToDefault(schema, DEFAULT_SCHEMA);
        tableName = blankToDefault(tableName, DEFAULT_TABLE);
    }

    public boolean configured() {
        return jdbcUrl != null;
    }

    /** JDBC 路径上的库名。{@code jdbc:postgresql://host/dream_scope} → {@code dream_scope}。 */
    public String databaseName() {
        return databaseName(jdbcUrl);
    }

    static String databaseName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        String stripped = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring("jdbc:".length()) : jdbcUrl;
        URI uri;
        try {
            uri = URI.create(stripped);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid pg jdbc-url: " + jdbcUrl, ex);
        }
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return null;
        }
        String name = path.startsWith("/") ? path.substring(1) : path;
        int slash = name.indexOf('/');
        if (slash >= 0) {
            name = name.substring(0, slash);
        }
        return name.isBlank() ? null : name;
    }

    /** 连维护库 {@code postgres}，用来建业务库。 */
    static String maintenanceJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("pg jdbc-url required");
        }
        int queryAt = jdbcUrl.indexOf('?');
        String base = queryAt >= 0 ? jdbcUrl.substring(0, queryAt) : jdbcUrl;
        String query = queryAt >= 0 ? jdbcUrl.substring(queryAt) : "";
        int slash = base.lastIndexOf('/');
        if (slash < 0 || slash == base.length() - 1) {
            throw new IllegalArgumentException("pg jdbc-url must include database name: " + jdbcUrl);
        }
        return base.substring(0, slash + 1) + "postgres" + query;
    }

    /**
     * 目标库不存在时先连 {@code postgres} 再建库。PgVectorStore 不会建 database。
     */
    public void ensureDatabase() {
        String db = databaseName();
        if (db == null || "postgres".equals(db)) {
            return;
        }
        if (!DB_NAME.matcher(db).matches()) {
            throw new IllegalArgumentException("pg database name must be [A-Za-z_][A-Za-z0-9_]*: " + db);
        }
        String adminUrl = maintenanceJdbcUrl(jdbcUrl);
        log.info("pgvector ensure database={} via {}", db, adminUrl);
        try (Connection conn = DriverManager.getConnection(adminUrl, username, password)) {
            if (databaseExists(conn, db)) {
                log.info("pgvector database exists name={}", db);
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE " + db);
            }
            log.info("pgvector database created name={}", db);
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "pgvector cannot create database " + db + ": " + ex.getMessage()
                            + "（也可手工: CREATE DATABASE " + db + ";）",
                    ex);
        }
    }

    private static boolean databaseExists(Connection conn, String db) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        String trimmed = blankToNull(value);
        return trimmed == null ? fallback : trimmed;
    }
}
