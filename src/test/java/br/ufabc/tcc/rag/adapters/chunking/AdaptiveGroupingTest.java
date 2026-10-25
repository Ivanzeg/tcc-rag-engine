package br.ufabc.tcc.rag.adapters.chunking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AdaptiveGroupingTest {

    private static final double TOLERANCE = 1e-6;
    private static final int NO_LIMIT = 1_000;

    private static final List<float[]> VECTORS = List.of(
            new float[] {1, 0},
            new float[] {1, 0},
            new float[] {0, 1},
            new float[] {1, 1});

    private static final int[] ONE_WORD_EACH = {1, 1, 1, 1};

    @Test
    void similaridadesConsecutivas() {
        double[] s = AdaptiveGrouping.similarities(VECTORS);
        assertArrayEquals(new double[] {1.0, 0.0, Math.sqrt(0.5)}, s, TOLERANCE);
    }

    @Test
    void cortaQuandoOCossenoFicaAbaixoDoLimiar() {
        assertEquals(List.of(List.of(0, 1), List.of(2, 3)),
                AdaptiveGrouping.group(VECTORS, ONE_WORD_EACH, 0.5, NO_LIMIT));
        assertEquals(List.of(List.of(0, 1), List.of(2), List.of(3)),
                AdaptiveGrouping.group(VECTORS, ONE_WORD_EACH, 0.8, NO_LIMIT));
    }

    @Test
    void cossenoIgualAoLimiarJunta() {
        assertEquals(List.of(List.of(0, 1), List.of(2), List.of(3)),
                AdaptiveGrouping.group(VECTORS, ONE_WORD_EACH, 1.0, NO_LIMIT));
    }

    @Test
    void cortaQuandoOTetoDePalavrasSeriaUltrapassado() {
        assertEquals(List.of(List.of(0, 1), List.of(2, 3)),
                AdaptiveGrouping.group(VECTORS, new int[] {2, 2, 2, 2}, -1.0, 5));
        assertEquals(List.of(List.of(0, 1, 2, 3)),
                AdaptiveGrouping.group(VECTORS, new int[] {2, 2, 2, 2}, -1.0, 8));
    }

    @Test
    void unidadeAcimaDoTetoFicaSozinha() {
        List<float[]> vectors = List.of(new float[] {1, 0}, new float[] {1, 0}, new float[] {1, 0});
        assertEquals(List.of(List.of(0), List.of(1), List.of(2)),
                AdaptiveGrouping.group(vectors, new int[] {10, 600, 10}, -1.0, 500));
    }

    @Test
    void comparaComAUltimaUnidadeDoChunk() {
        List<float[]> vectors = List.of(new float[] {1, 0}, new float[] {1, 1}, new float[] {0, 1});
        assertEquals(List.of(List.of(0, 1, 2)), AdaptiveGrouping.group(vectors, new int[] {1, 1, 1}, 0.7, NO_LIMIT));
    }

    @Test
    void casosDeBorda() {
        assertTrue(AdaptiveGrouping.group(List.of(), new int[0], 0.5, NO_LIMIT).isEmpty());
        assertEquals(List.of(List.of(0)), AdaptiveGrouping.group(List.of(new float[] {1, 0}), new int[] {3}, 0.5, 1));
        assertEquals(0.0, AdaptiveGrouping.cosine(new float[] {0, 0}, new float[] {1, 0}));
        assertThrows(IllegalArgumentException.class,
                () -> AdaptiveGrouping.group(VECTORS, new int[] {1, 1}, 0.5, NO_LIMIT));
        assertThrows(IllegalArgumentException.class,
                () -> AdaptiveGrouping.cosine(new float[] {1, 0}, new float[] {1, 0, 0}));
    }
}
