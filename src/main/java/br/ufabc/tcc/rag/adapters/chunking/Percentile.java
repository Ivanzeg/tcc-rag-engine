package br.ufabc.tcc.rag.adapters.chunking;

import java.util.Arrays;

public final class Percentile {

    public static final String METHOD = "linear (Hyndman-Fan tipo 7, padrão do numpy.percentile)";

    private Percentile() {
    }

    public static double linear(double[] values, double p) {
        if (values.length == 0) {
            throw new IllegalArgumentException("Não há valores para calcular o percentil");
        }
        if (p < 0 || p > 100) {
            throw new IllegalArgumentException("O percentil deve estar entre 0 e 100: " + p);
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double h = (sorted.length - 1) * p / 100.0;
        int lower = (int) Math.floor(h);
        if (lower >= sorted.length - 1) {
            return sorted[sorted.length - 1];
        }
        return sorted[lower] + (h - lower) * (sorted[lower + 1] - sorted[lower]);
    }
}
