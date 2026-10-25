package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.vector.PgVectorIndex;
import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.QueryMetrics;
import br.ufabc.tcc.rag.core.domain.QueryResult;
import br.ufabc.tcc.rag.core.domain.Stratum;
import br.ufabc.tcc.rag.core.port.Chunker;
import br.ufabc.tcc.rag.core.service.Evaluator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class EvaluateCommand {

    private static final int WARMUP_QUERIES = 3;
    private static final int LLM_CALLS = 0;

    private final ExperimentServices services;

    public EvaluateCommand(ExperimentServices services) {
        this.services = services;
    }

    public void run(String strategy) {
        List<GoldQuery> queries = services.queries();
        Map<String, float[]> queryVectors = services.queryVectors(queries);
        printSummary(strategy, evaluate(strategy, queries, queryVectors));
    }

    public List<QueryResult> evaluate(String strategy, List<GoldQuery> queries, Map<String, float[]> queryVectors) {
        CountingEmbeddingProvider embeddings = new CountingEmbeddingProvider(services.embeddings());
        Chunker chunker = services.chunkerFor(strategy, embeddings);
        PgVectorIndex index = services.index();

        long startedAt = System.nanoTime();
        List<Chunk> chunks = new StrategyIndexer().index(chunker, services.corpus(), embeddings, index,
                ExperimentServices.CHUNKS_DIR);
        double indexSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        int embeddingInputs = embeddings.submittedTexts();

        List<QueryResult> results = new Evaluator(index, services.config().experimentK())
                .evaluate(queries, queryVectors, WARMUP_QUERIES);

        ResultWriter writer = new ResultWriter(ExperimentServices.RESULTS_DIR);
        writer.writeRetrieval(strategy, results);
        writer.writeMetrics(strategy, results);
        writer.appendIndexingCost(strategy, chunks.size(), indexSeconds, LLM_CALLS, embeddingInputs);
        return results;
    }

    static void printSummary(String strategy, List<QueryResult> results) {
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

    private static void printLine(String label, List<QueryMetrics> group) {
        System.out.println(String.format(Locale.ROOT, "%-15s n=%-3d P@5=%.4f R@5=%.4f F1@5=%.4f MRR@5=%.4f",
                label, group.size(),
                mean(group, QueryMetrics::precision), mean(group, QueryMetrics::recall),
                mean(group, QueryMetrics::f1), mean(group, QueryMetrics::reciprocalRank)));
    }

    private static double mean(List<QueryMetrics> group, ToDoubleFunction<QueryMetrics> field) {
        return group.stream().mapToDouble(field).average().orElse(0);
    }
}
