package br.ufabc.tcc.rag.adapters.chunking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TfIdfTest {

    private static final double TOLERANCE = 1e-9;

    private final TfIdf tfIdf = new TfIdf(Set.of("de", "o", "a"));

    @Test
    void tokenizaEmCaixaBaixaSemPontuacaoESemStopwords() {
        assertEquals(List.of("art", "5", "inciso", "i", "controlador"),
                tfIdf.tokenize("Art. 5, inciso I-A: o Controlador"));
    }

    @Test
    void idfSuavizadoENormalizacaoL2() {
        List<double[]> vectors = tfIdf.vectorize(List.of("Gato preto", "gato branco", "de"));

        double idfGato = Math.log(4.0 / 3.0) + 1;
        double idfPreto = Math.log(4.0 / 2.0) + 1;
        assertEquals(1.2876820724517808, idfGato, TOLERANCE);
        assertEquals(1.6931471805599454, idfPreto, TOLERANCE);

        assertArrayEquals(new double[] {0, 0.6053485081062916, 0.7959605415681652}, vectors.get(0), TOLERANCE);
        assertArrayEquals(new double[] {0.7959605415681652, 0.6053485081062916, 0}, vectors.get(1), TOLERANCE);
        assertArrayEquals(new double[] {0, 0, 0}, vectors.get(2), TOLERANCE);
    }

    @Test
    void frequenciaDoTermoEhAContagemBruta() {
        List<double[]> vectors = tfIdf.vectorize(List.of("gato gato preto", "preto"));
        double idfGato = Math.log(3.0 / 2.0) + 1;
        double idfPreto = Math.log(3.0 / 3.0) + 1;
        double gato = 2 * idfGato;
        double norm = Math.sqrt(gato * gato + idfPreto * idfPreto);
        assertArrayEquals(new double[] {gato / norm, idfPreto / norm}, vectors.get(0), TOLERANCE);
        assertArrayEquals(new double[] {0, 1}, vectors.get(1), TOLERANCE);
    }
}
