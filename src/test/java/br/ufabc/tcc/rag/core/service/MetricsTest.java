package br.ufabc.tcc.rag.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.ufabc.tcc.rag.core.domain.Hit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MetricsTest {

    private static final double TOLERANCE = 1e-4;
    private static final int K = 5;

    @Test
    void casoUmComUmaEvidenciaNaSegundaPosicao() {
        Set<String> evidence = Set.of("u1");
        List<Hit> hits = hits(List.of("u9"), List.of("u1", "u2"), List.of("u3"), List.of("u1"), List.of("u7"));
        assertCase(evidence, hits, 0.4, 1.0, 0.5714, 0.5);
    }

    @Test
    void casoDoisComTresEvidenciasEDuasRecuperadas() {
        Set<String> evidence = Set.of("u1", "u5", "u8");
        List<Hit> hits = hits(List.of("u1"), List.of("u2"), List.of("u5"), List.of("u3"), List.of("u4"));
        assertCase(evidence, hits, 0.4, 0.6667, 0.5, 1.0);
    }

    @Test
    void casoTresSemNenhumaEvidenciaRecuperada() {
        Set<String> evidence = Set.of("u1");
        List<Hit> hits = hits(List.of("u2"), List.of("u3"), List.of("u4"), List.of("u5"), List.of("u6"));
        assertCase(evidence, hits, 0.0, 0.0, 0.0, 0.0);
    }

    @Test
    void precisaoDivideSemprePorKMesmoComMenosResultados() {
        List<Hit> hits = hits(List.of("u1"));
        assertEquals(0.2, Metrics.precision(Set.of("u1"), hits, K), TOLERANCE,
                "A precisão deve dividir por k, não pelo número de resultados");
    }

    private static void assertCase(Set<String> evidence, List<Hit> hits, double p, double r, double f1, double rr) {
        double precision = Metrics.precision(evidence, hits, K);
        double recall = Metrics.recall(evidence, hits, K);
        assertEquals(p, precision, TOLERANCE, "P@5 divergente");
        assertEquals(r, recall, TOLERANCE, "R@5 divergente");
        assertEquals(f1, Metrics.f1(precision, recall), TOLERANCE, "F1@5 divergente");
        assertEquals(rr, Metrics.reciprocalRank(evidence, hits, K), TOLERANCE, "RR@5 divergente");
    }

    @SafeVarargs
    private static List<Hit> hits(List<String>... unitIdsPerHit) {
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < unitIdsPerHit.length; i++) {
            hits.add(new Hit(i + 1, "c" + (i + 1), 0.5, unitIdsPerHit[i]));
        }
        return hits;
    }
}
