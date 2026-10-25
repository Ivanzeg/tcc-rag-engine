package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.core.domain.Hit;
import br.ufabc.tcc.rag.core.domain.QueryMetrics;
import br.ufabc.tcc.rag.core.domain.QueryResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ResultWriter {

    private static final String RETRIEVAL_HEADER = "strategy,query_id,stratum,rank,chunk_id,score,unit_ids,is_relevant";
    private static final String METRICS_HEADER =
            "strategy,query_id,stratum,n_evidence,p_at_5,r_at_5,f1_at_5,rr_at_5,latency_ms";
    private static final String COST_HEADER =
            "strategy,n_chunks,index_seconds,llm_calls,embedding_inputs,timestamp";

    private final Path resultsDir;

    public ResultWriter(Path resultsDir) {
        this.resultsDir = resultsDir;
    }

    public void writeRetrieval(String strategy, List<QueryResult> results) {
        List<String> lines = new ArrayList<>();
        lines.add(RETRIEVAL_HEADER);
        for (QueryResult result : results) {
            for (int i = 0; i < result.hits().size(); i++) {
                Hit hit = result.hits().get(i);
                lines.add(String.join(",", strategy, result.query().id(), result.query().stratum().name(),
                        String.valueOf(hit.rank()), hit.chunkId(),
                        String.format(Locale.ROOT, "%.6f", hit.score()),
                        String.join(";", hit.unitIds()), String.valueOf(result.relevance().get(i))));
            }
        }
        write(resultsDir.resolve("retrieval_" + strategy + ".csv"), lines);
    }

    public void writeMetrics(String strategy, List<QueryResult> results) {
        List<String> lines = new ArrayList<>();
        lines.add(METRICS_HEADER);
        for (QueryResult result : results) {
            QueryMetrics m = result.metrics();
            lines.add(String.join(",", strategy, m.queryId(), m.stratum().name(), String.valueOf(m.nEvidence()),
                    decimal(m.precision()), decimal(m.recall()), decimal(m.f1()), decimal(m.reciprocalRank()),
                    decimal(m.latencyMs())));
        }
        write(resultsDir.resolve("metrics_" + strategy + ".csv"), lines);
    }

    public void appendIndexingCost(String strategy, int chunkCount, double indexSeconds, int llmCalls,
            int embeddingInputs) {
        Path file = resultsDir.resolve("indexing_cost.csv");
        List<String> lines = new ArrayList<>();
        if (!Files.exists(file)) {
            lines.add(COST_HEADER);
        }
        lines.add(String.join(",", strategy, String.valueOf(chunkCount),
                String.format(Locale.ROOT, "%.3f", indexSeconds), String.valueOf(llmCalls),
                String.valueOf(embeddingInputs), OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        try {
            Files.createDirectories(resultsDir);
            Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao anexar em " + file, e);
        }
    }

    private void write(Path file, List<String> lines) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + file, e);
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
