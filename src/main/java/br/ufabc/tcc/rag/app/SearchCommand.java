package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.embedding.OpenAiEmbeddingProvider;
import br.ufabc.tcc.rag.adapters.vector.PgVectorIndex;
import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Hit;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import br.ufabc.tcc.rag.core.port.Chunker;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SearchCommand {

    private static final String STRATEGY = "fixed";
    private static final int PREVIEW_LENGTH = 120;
    private static final Path SQL_FILE = Path.of("target", "last_search.sql");

    private final ExperimentServices services;
    private final Config config;

    public SearchCommand(ExperimentServices services) {
        this.services = services;
        this.config = services.config();
    }

    public void run(String query) {
        Corpus corpus = services.corpus();
        OpenAiEmbeddingProvider embeddings = services.embeddings();
        PgVectorIndex index = services.index();
        Chunker chunker = services.chunkerFor(STRATEGY, embeddings);
        List<Chunk> chunks = new StrategyIndexer().index(chunker, corpus, embeddings, index,
                ExperimentServices.CHUNKS_DIR);

        float[] queryVector = embeddings.embed(query);
        int k = config.experimentK();
        List<Hit> hits = index.search(queryVector, k);

        printHits(query, hits, corpus, chunks);
        printSql(chunker.name(), queryVector, k);
    }

    private void printHits(String query, List<Hit> hits, Corpus corpus, List<Chunk> chunks) {
        Map<String, Unit> unitsById = corpus.units().stream()
                .collect(Collectors.toMap(Unit::id, Function.identity()));
        Map<String, Chunk> chunksById = chunks.stream()
                .collect(Collectors.toMap(Chunk::id, Function.identity()));
        System.out.println();
        System.out.println("Consulta: " + query);
        System.out.println("Top-" + hits.size() + " (cosseno):");
        for (Hit hit : hits) {
            Set<String> articles = new LinkedHashSet<>();
            for (String unitId : hit.unitIds()) {
                Unit unit = unitsById.get(unitId);
                if (unit != null && unit.type() == UnitType.PROVISION && !unit.article().isBlank()) {
                    articles.add(unit.article());
                }
            }
            String articleText = articles.isEmpty() ? "(sem artigo)" : String.join(", ", articles);
            String text = chunksById.get(hit.chunkId()).text().replaceAll("\\s+", " ").strip();
            String preview = text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
            System.out.println(String.format(Locale.ROOT, "%d. cosseno=%.4f | %s | %s",
                    hit.rank(), hit.score(), hit.chunkId(), articleText));
            System.out.println("   " + preview);
        }
    }

    private void printSql(String strategy, float[] queryVector, int k) {
        String table = "chunks_" + strategy;
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < queryVector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(queryVector[i]);
        }
        literal.append(']');
        String template = "WITH q AS (SELECT '%s'::vector AS v)\n"
                + "SELECT c.chunk_id, c.unit_ids, 1 - (c.embedding <=> q.v) AS cosseno\n"
                + "FROM " + table + " c, q\n"
                + "ORDER BY c.embedding <=> q.v\n"
                + "LIMIT " + k + ";\n";
        String full = String.format(template, literal);
        String shortForm = String.format(template, "[" + queryVector[0] + "," + queryVector[1] + ",...,"
                + queryVector[queryVector.length - 1] + "]");
        try {
            Files.createDirectories(SQL_FILE.getParent());
            Files.writeString(SQL_FILE, full, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + SQL_FILE, e);
        }
        System.out.println();
        System.out.println("SQL equivalente (vetor abreviado; operador <=> é a distância de cosseno):");
        System.out.println(shortForm);
        System.out.println("SQL completo gravado em " + SQL_FILE + ". Para rodar no psql:");
        System.out.println("  " + psqlCommand());
    }

    private String psqlCommand() {
        URI uri = URI.create(config.dbUrl().substring("jdbc:".length()));
        String database = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
        return "psql -h " + uri.getHost() + " -p " + uri.getPort() + " -U " + config.dbUser()
                + " -d " + database + " -f " + SQL_FILE;
    }
}
