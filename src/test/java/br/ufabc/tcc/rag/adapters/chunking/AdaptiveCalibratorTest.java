package br.ufabc.tcc.rag.adapters.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.ufabc.tcc.rag.adapters.chunking.AdaptiveCalibrator.Candidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveCalibratorTest {

    private static final double TOLERANCE = 1e-9;

    private static final List<float[]> VECTORS = List.of(
            new float[] {1, 0},
            new float[] {1, 0},
            new float[] {0, 1},
            new float[] {1, 1});

    private static final List<String> TEXTS = List.of("aa", "bb", "cc", "dd");

    @Test
    void escolheOCandidatoMaisProximoDoAlvo() {
        List<Candidate> candidates = List.of(
                candidate(5, 300), candidate(10, 40), candidate(15, 12), candidate(20, 90));
        assertEquals(15, AdaptiveCalibrator.choose(candidates).percentile());
    }

    @Test
    void empateFicaComOMenorPercentil() {
        List<Candidate> candidates = List.of(
                candidate(30, 10), candidate(20, 10), candidate(25, 50));
        assertEquals(20, AdaptiveCalibrator.choose(candidates).percentile());
    }

    @Test
    void simulaOChunkingDeCadaPercentil() {
        List<Candidate> candidates = AdaptiveCalibrator.candidates(VECTORS, TEXTS, 100, 4.0);
        assertEquals(AdaptiveCalibrator.PERCENTILES.size(), candidates.size());

        Candidate p50 = candidates.stream().filter(c -> c.percentile() == 50).findFirst().orElseThrow();
        assertEquals(Math.sqrt(0.5), p50.tau(), 1e-6);
        assertEquals(2, p50.chunks());
        assertEquals(5.0, p50.meanChars(), TOLERANCE);
        assertEquals(2.0, p50.meanWords(), TOLERANCE);
        assertEquals(1.0, p50.distance(), TOLERANCE);
    }

    @Test
    void tetoDePalavrasEntraNaSimulacao() {
        List<Candidate> candidates = AdaptiveCalibrator.candidates(VECTORS, TEXTS, 1, 4.0);
        for (Candidate candidate : candidates) {
            assertEquals(4, candidate.chunks(), "p=" + candidate.percentile());
            assertEquals(2.0, candidate.meanChars(), TOLERANCE);
        }
    }

    private static Candidate candidate(int percentile, double distance) {
        return new Candidate(percentile, 0.5, 1, 100, 10, distance);
    }
}
