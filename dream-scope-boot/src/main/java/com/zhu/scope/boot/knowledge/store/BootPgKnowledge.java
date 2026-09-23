package com.zhu.scope.boot.knowledge.store;

import com.zhu.scope.boot.config.BootScopeProperties;
import com.zhu.scope.boot.knowledge.index.BootKnowledgeIndex;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.store.PgVectorStore;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DashScope 向量写入 PostgreSQL pgvector。JDBC 地址为空时不创建。 */
@SuppressWarnings({"deprecation", "removal"})
public final class BootPgKnowledge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BootPgKnowledge.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Pattern DB_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final SimpleKnowledge knowledge;

    private final PgVectorStore store;

    private BootPgKnowledge(SimpleKnowledge knowledge, PgVectorStore store) {
        this.knowledge = knowledge;
        this.store = store;
    }

    public static BootPgKnowledge open(BootScopeProperties.Knowledge knowledge) {
        if (knowledge == null || knowledge.getPg() == null) {
            return null;
        }
        String jdbcUrl = knowledge.getPg().getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        if (knowledge.getApiKey() == null || knowledge.getApiKey().isBlank()) {
            log.warn("knowledge pg jdbc-url is set but api key is empty, skip vector store");
            return null;
        }
        BootScopeProperties.Knowledge.Pg pg = knowledge.getPg();
        ensureDatabase(jdbcUrl.trim(), pg.getUsername(), pg.getPassword());
        EmbeddingModel model = DashScopeTextEmbedding.builder()
                .apiKey(knowledge.getApiKey().trim())
                .modelName(blank(knowledge.getEmbeddingModel(), "text-embedding-v3"))
                .dimensions(knowledge.getEmbeddingDimensions() > 0 ? knowledge.getEmbeddingDimensions() : 1024)
                .build();
        try {
            PgVectorStore store = PgVectorStore.builder()
                    .jdbcUrl(jdbcUrl.trim())
                    .username(blank(pg.getUsername(), "postgres"))
                    .password(pg.getPassword() == null ? "" : pg.getPassword())
                    .schema(blank(pg.getSchema(), "public"))
                    .tableName(blank(pg.getTable(), "dream_scope_boot_rag"))
                    .dimensions(model.getDimensions())
                    .build();
            SimpleKnowledge simple = SimpleKnowledge.builder()
                    .embeddingModel(model)
                    .embeddingStore(store)
                    .build();
            log.info("knowledge pg store jdbc={} table={}", jdbcUrl.trim(), store.getTableName());
            return new BootPgKnowledge(simple, store);
        } catch (Exception ex) {
            throw new IllegalStateException("pgvector store failed: " + jdbcUrl, ex);
        }
    }

    public void add(String id, String text, String source) {
        DocumentMetadata meta = DocumentMetadata.builder()
                .content(TextBlock.builder().text(text).build())
                .docId(id)
                .chunkId(id)
                .addPayload("source", source == null ? "" : source)
                .build();
        knowledge.addDocuments(List.of(new Document(meta))).block(TIMEOUT);
    }

    public void delete(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        try {
            store.delete(id).block(TIMEOUT);
        } catch (RuntimeException ex) {
            log.warn("knowledge pg delete id={} failed: {}", id, ex.getMessage());
        }
    }

    public List<BootKnowledgeIndex.Hit> search(String query, int topK, double minScore) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        double floor = minScore < 0 ? 0 : Math.min(minScore, 1);
        try {
            RetrieveConfig config = RetrieveConfig.builder().limit(topK).scoreThreshold(floor).build();
            List<Document> docs = knowledge.retrieve(query, config).block(TIMEOUT);
            if (docs == null || docs.isEmpty()) {
                return List.of();
            }
            List<BootKnowledgeIndex.Hit> hits = new ArrayList<>(docs.size());
            for (Document doc : docs) {
                DocumentMetadata meta = doc.getMetadata();
                String text = meta == null ? "" : meta.getContentText();
                String id = meta == null ? "" : meta.getDocId();
                double score = doc.getScore() == null ? 0.0 : doc.getScore();
                if (text != null && !text.isBlank() && score + 1e-9 >= floor) {
                    Object raw = doc.getPayloadValue("source");
                    String src = raw == null ? "" : String.valueOf(raw);
                    hits.add(new BootKnowledgeIndex.Hit(id == null ? "" : id, text, score, src));
                }
            }
            return List.copyOf(hits);
        } catch (RuntimeException ex) {
            log.warn("knowledge pg search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    @Override
    public void close() {
        store.close();
    }

    private static void ensureDatabase(String jdbcUrl, String username, String password) {
        String db = databaseName(jdbcUrl);
        if (db == null || "postgres".equals(db)) {
            return;
        }
        if (!DB_NAME.matcher(db).matches()) {
            throw new IllegalArgumentException("pg database name must be [A-Za-z_][A-Za-z0-9_]*: " + db);
        }
        String adminUrl = maintenanceJdbcUrl(jdbcUrl);
        String user = blank(username, "postgres");
        String pass = password == null ? "" : password;
        try (Connection conn = DriverManager.getConnection(adminUrl, user, pass)) {
            if (databaseExists(conn, db)) {
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE DATABASE " + db);
            }
            log.info("pgvector database created name={}", db);
        } catch (SQLException ex) {
            throw new IllegalStateException("pgvector cannot create database " + db, ex);
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

    static String databaseName(String jdbcUrl) {
        String stripped = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring("jdbc:".length()) : jdbcUrl;
        URI uri = URI.create(stripped);
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

    private static String maintenanceJdbcUrl(String jdbcUrl) {
        int queryAt = jdbcUrl.indexOf('?');
        String base = queryAt >= 0 ? jdbcUrl.substring(0, queryAt) : jdbcUrl;
        String query = queryAt >= 0 ? jdbcUrl.substring(queryAt) : "";
        int slash = base.lastIndexOf('/');
        if (slash < 0 || slash == base.length() - 1) {
            throw new IllegalArgumentException("pg jdbc-url must include database name");
        }
        return base.substring(0, slash + 1) + "postgres" + query;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
