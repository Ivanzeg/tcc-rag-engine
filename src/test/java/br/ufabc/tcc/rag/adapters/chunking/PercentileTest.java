package br.ufabc.tcc.rag.adapters.chunking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PercentileTest {

    private static final double TOLERANCE = 1e-12;

    @Test
    void interpolaLinearmenteEntreAsPosicoesVizinhas() {
        double[] values = {1, 2, 3, 4, 5};
        assertEquals(1.0, Percentile.linear(values, 0), TOLERANCE);
        assertEquals(1.4, Percentile.linear(values, 10), TOLERANCE);
        assertEquals(2.0, Percentile.linear(values, 25), TOLERANCE);
        assertEquals(3.0, Percentile.linear(values, 50), TOLERANCE);
        assertEquals(5.0, Percentile.linear(values, 100), TOLERANCE);
    }

    @Test
    void doisValores() {
        double[] values = {10, 20};
        assertEquals(10.5, Percentile.linear(values, 5), TOLERANCE);
        assertEquals(15.0, Percentile.linear(values, 50), TOLERANCE);
    }

    @Test
    void ordenaSemAlterarAEntrada() {
        double[] values = {5, 1, 4, 2, 3};
        assertEquals(1.4, Percentile.linear(values, 10), TOLERANCE);
        assertArrayEquals(new double[] {5, 1, 4, 2, 3}, values, "A entrada foi alterada");
    }

    @Test
    void valorUnico() {
        assertEquals(7.0, Percentile.linear(new double[] {7}, 35), TOLERANCE);
    }

    @Test
    void rejeitaEntradaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> Percentile.linear(new double[0], 50));
        assertThrows(IllegalArgumentException.class, () -> Percentile.linear(new double[] {1}, 101));
        assertThrows(IllegalArgumentException.class, () -> Percentile.linear(new double[] {1}, -1));
    }
}
