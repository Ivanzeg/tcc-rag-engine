package br.ufabc.tcc.rag.core.service;

import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.Hit;
import br.ufabc.tcc.rag.core.domain.QueryMetrics;
import br.ufabc.tcc.rag.core.domain.QueryResult;
import br.ufabc.tcc.rag.core.port.VectorIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Evaluator {

    private final VectorIndex index;
    private final int k;

    public Evaluator(VectorIndex index, int k) {
        this.index = index;
        this.k = k;
    }

    public List<QueryResult> evaluate(List<GoldQuery> queries, Map<String, float[]> queryVectors, int warmup) {
        for (int i = 0; i < Math.min(warmup, queries.size()); i++) {
            index.search(vectorOf(queries.get(i), queryVectors), k);
        }
        List<QueryResult> results = new ArrayList<>();
        for (GoldQuery query : queries) {
            results.add(evaluateOne(query, vectorOf(query, queryVectors)));
        }
        return results;
    }

    private QueryResult evaluateOne(GoldQuery query, float[] vector) {
        long startedAt = System.nanoTime();
        List<Hit> hits = index.search(vector, k);
        double latencyMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        Set<String> evidence = new HashSet<>(query.evidenceUnitIds());
        List<Boolean> relevance = new ArrayList<>();
        for (Hit hit : hits) {
            relevance.add(Metrics.isRelevant(evidence, hit));
        }
        double precision = Metrics.precision(evidence, hits, k);
        double recall = Metrics.recall(evidence, hits, k);
        QueryMetrics metrics = new QueryMetrics(query.id(), query.stratum(), evidence.size(), precision, recall,
                Metrics.f1(precision, recall), Metrics.reciprocalRank(evidence, hits, k), latencyMs);
        return new QueryResult(query, hits, relevance, metrics);
    }

    private static float[] vectorOf(GoldQuery query, Map<String, float[]> queryVectors) {
        float[] vector = queryVectors.get(query.id());
        if (vector == null) {
            throw new IllegalStateException("Não há vetor de consulta para " + query.id());
        }
        return vector;
    }
}
