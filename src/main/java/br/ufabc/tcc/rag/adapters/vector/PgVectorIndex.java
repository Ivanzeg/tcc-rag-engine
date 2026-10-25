package br.ufabc.tcc.rag.adapters.vector;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Hit;
import br.ufabc.tcc.rag.core.port.VectorIndex;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

public final class PgVectorIndex implements VectorIndex {

    public record Settings(String documentoId, String versao, String hashCorpus, String modeloEmbedding) {
    }

    private static final Pattern STRATEGY_NAME = Pattern.compile("[a-z]+");
    private static final String TABLE_PREFIX = "chunks_";
    private static final double MIN_SCORE = 0.0;
    private static final List<String> METADATA_COLUMNS = List.of(
            "chunk_id text not null",
            "unit_ids text not null",
            "documento_id text",
            "versao text",
            "hash_corpus text",
            "condicao text",
            "modelo_embedding text");

    private final DataSource dataSource;
    private final int dimension;
    private final Settings settings;
    private String strategy;
    private String table;
    private PgVectorEmbeddingStore store;

    public PgVectorIndex(String url, String user, String password, int dimension, Settings settings,
            Path environmentFile) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(url);
        source.setUser(user);
        source.setPassword(password);
        this.dataSource = source;
        this.dimension = dimension;
        this.settings = settings;
        verifyEnvironment(url, environmentFile);
    }

    @Override
    public void reset(String strategy) {
        if (!STRATEGY_NAME.matcher(strategy).matches()) {
            throw new IllegalArgumentException("Nome de estratégia inválido para tabela: " + strategy);
        }
        this.strategy = strategy;
        this.table = TABLE_PREFIX + strategy;
        this.store = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(table)
                .dimension(dimension)
                .useIndex(false)
                .createTable(true)
                .dropTableFirst(true)
                .skipCreateVectorExtension(true)
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                        .columnDefinitions(METADATA_COLUMNS)
                        .build())
                .build();
    }

    @Override
    public void addAll(List<Chunk> chunks, List<float[]> vectors) {
        requireReset();
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("Número de chunks (" + chunks.size()
                    + ") diferente do número de vetores (" + vectors.size() + ")");
        }
        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            ids.add(UUID.nameUUIDFromBytes((strategy + ":" + chunk.id()).getBytes(StandardCharsets.UTF_8))
                    .toString());
            embeddings.add(Embedding.from(vectors.get(i)));
            segments.add(TextSegment.from(chunk.text(), metadataOf(chunk)));
        }
        store.addAll(ids, embeddings, segments);
        createHnswIndex();
    }

    @Override
    public List<Hit> search(float[] query, int k) {
        requireReset();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(query))
                .maxResults(k)
                .minScore(MIN_SCORE)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            Metadata metadata = match.embedded().metadata();
            List<String> unitIds = Arrays.asList(metadata.getString("unit_ids").split(","));
            double cosine = 2 * match.score() - 1;
            hits.add(new Hit(i + 1, metadata.getString("chunk_id"), cosine, unitIds));
        }
        return hits;
    }

    private Metadata metadataOf(Chunk chunk) {
        return new Metadata()
                .put("chunk_id", chunk.id())
                .put("unit_ids", String.join(",", chunk.unitIds()))
                .put("documento_id", settings.documentoId())
                .put("versao", settings.versao())
                .put("hash_corpus", settings.hashCorpus())
                .put("condicao", strategy)
                .put("modelo_embedding", settings.modeloEmbedding());
    }

    private void requireReset() {
        if (store == null) {
            throw new IllegalStateException("Chame reset(estrategia) antes de usar o índice");
        }
    }

    private void createHnswIndex() {
        String sql = "CREATE INDEX ON " + table + " USING hnsw (embedding vector_cosine_ops)";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o índice HNSW em " + table + ": " + e.getMessage(), e);
        }
    }

    private void verifyEnvironment(String url, Path environmentFile) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            String extensionVersion;
            try (ResultSet rs = statement.executeQuery(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'")) {
                if (!rs.next()) {
                    throw new IllegalStateException("A extensão 'vector' não está instalada no banco " + url
                            + ". Ela não será criada pelo programa.");
                }
                extensionVersion = rs.getString(1);
            }
            String postgresVersion;
            try (ResultSet rs = statement.executeQuery("SELECT version()")) {
                rs.next();
                postgresVersion = rs.getString(1);
            }
            writeEnvironment(environmentFile, extensionVersion, postgresVersion);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao conectar ao banco " + url + ": " + e.getMessage(), e);
        }
    }

    private static void writeEnvironment(Path file, String extensionVersion, String postgresVersion) {
        String content = "pgvector=" + extensionVersion + "\n"
                + "postgresql=" + postgresVersion + "\n"
                + "java=" + System.getProperty("java.version") + "\n";
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + file, e);
        }
    }
}
