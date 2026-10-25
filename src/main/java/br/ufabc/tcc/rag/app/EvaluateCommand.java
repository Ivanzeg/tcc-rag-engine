package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.adapters.chunking.FixedChunker;
import br.ufabc.tcc.rag.adapters.embedding.OpenAiEmbeddingProvider;
import br.ufabc.tcc.rag.adapters.vector.PgVectorIndex;
import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.QueryMetrics;
import br.ufabc.tcc.rag.core.domain.QueryResult;
import br.ufabc.tcc.rag.core.domain.Stratum;
import br.ufabc.tcc.rag.core.port.Chunker;
import br.ufabc.tcc.rag.core.service.Evaluator;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EvaluateCommand {

    private static final String DOCUMENT_ID = "lgpd-13709-2018";
    private static final String DOCUMENT_VERSION = "v1";
    private static final int WARMUP_QUERIES = 3;
    private static final int LLM_CALLS = 0;

    private final Config config;

    public EvaluateCommand(Config config) {
        this.config = config;
    }

    public void run(String strategy) {
        Chunker chunker = chunkerFor(strategy);
        Corpus corpus = new CorpusLoader().load(Path.of("data", "corpus"));
        OpenAiEmbeddingProvider openAi = new OpenAiEmbeddingProvider(
                config.openAiApiKey(), config.openAiEmbeddingModel(), Path.of("data", "cache", "embeddings"));
        CountingEmbeddingProvider embeddings = new CountingEmbeddingProvider(openAi);
        PgVectorIndex index = new PgVectorIndex(
                config.dbUrl(), config.dbUser(), config.dbPassword(), embeddings.dimension(),
                new PgVectorIndex.Settings(DOCUMENT_ID, DOCUMENT_VERSION, Sha256.hex(corpus.canonicalText()),
                        config.openAiEmbeddingModel()),
                Path.of("results", "environment.txt"));

        long startedAt = System.nanoTime();
        List<Chunk> chunks = new StrategyIndexer().index(chunker, corpus, embeddings, index, Path.of("data", "chunks"));
        double indexSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        int embeddingInputs = embeddings.submittedTexts();

        List<GoldQuery> queries = new GoldenLoader().load(Path.of("data", "golden", "queries_resolved.json"));
        Map<String, float[]> queryVectors = new QueryEmbeddingStore(
                Path.of("data", "cache", "query_embeddings.json"), config.openAiEmbeddingModel(), openAi)
                .load(queries);

        List<QueryResult> results = new Evaluator(index, config.experimentK())
                .evaluate(queries, queryVectors, WARMUP_QUERIES);

        ResultWriter writer = new ResultWriter(Path.of("results"));
        writer.writeRetrieval(strategy, results);
        writer.writeMetrics(strategy, results);
        writer.appendIndexingCost(strategy, chunks.size(), indexSeconds, LLM_CALLS, embeddingInputs);

        printSummary(strategy, results);
    }

    private Chunker chunkerFor(String strategy) {
        if ("fixed".equals(strategy)) {
            return new FixedChunker();
        }
        throw new IllegalStateException("Estratégia não implementada nesta etapa: " + strategy);
    }

    private void printSummary(String strategy, List<QueryResult> results) {
        System.out.println();
        System.out.println("Médias (" + strategy + "):");
        for (Stratum stratum : Stratum.values()) {
            List<QueryMetrics> group = results.stream()
                    .map(QueryResult::metrics)
                    .filter(m -> m.stratum() == stratum)
                    .toList();
            printLine(stratum.name(), group);
        }
        printLine("GLOBAL", results.stream().map(QueryResult::metrics).toList());
    }

    private void printLine(String label, List<QueryMetrics> group) {
        System.out.println(String.format(Locale.ROOT, "%-15s n=%-3d P@5=%.4f R@5=%.4f F1@5=%.4f MRR@5=%.4f",
                label, group.size(),
                mean(group, QueryMetrics::precision), mean(group, QueryMetrics::recall),
                mean(group, QueryMetrics::f1), mean(group, QueryMetrics::reciprocalRank)));
    }

    private static double mean(List<QueryMetrics> group, java.util.function.ToDoubleFunction<QueryMetrics> field) {
        return group.stream().mapToDouble(field).average().orElse(0);
    }
}
