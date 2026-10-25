package br.ufabc.tcc.rag.core.service;

import br.ufabc.tcc.rag.core.domain.Hit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Metrics {

    private Metrics() {
    }

    public static boolean isRelevant(Set<String> evidence, Hit hit) {
        for (String unitId : hit.unitIds()) {
            if (evidence.contains(unitId)) {
                return true;
            }
        }
        return false;
    }

    public static double precision(Set<String> evidence, List<Hit> hits, int k) {
        int relevant = 0;
        for (Hit hit : top(hits, k)) {
            if (isRelevant(evidence, hit)) {
                relevant++;
            }
        }
        return (double) relevant / k;
    }

    public static double recall(Set<String> evidence, List<Hit> hits, int k) {
        Set<String> covered = new HashSet<>();
        for (Hit hit : top(hits, k)) {
            for (String unitId : hit.unitIds()) {
                if (evidence.contains(unitId)) {
                    covered.add(unitId);
                }
            }
        }
        return (double) covered.size() / evidence.size();
    }

    public static double f1(double precision, double recall) {
        if (precision + recall == 0) {
            return 0;
        }
        return 2 * precision * recall / (precision + recall);
    }

    public static double reciprocalRank(Set<String> evidence, List<Hit> hits, int k) {
        List<Hit> topHits = top(hits, k);
        for (int i = 0; i < topHits.size(); i++) {
            if (isRelevant(evidence, topHits.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    private static List<Hit> top(List<Hit> hits, int k) {
        return hits.size() <= k ? hits : hits.subList(0, k);
    }
}
