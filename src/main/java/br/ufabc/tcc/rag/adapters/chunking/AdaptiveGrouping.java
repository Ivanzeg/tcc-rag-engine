package br.ufabc.tcc.rag.adapters.chunking;

import java.util.ArrayList;
import java.util.List;

public final class AdaptiveGrouping {

    private AdaptiveGrouping() {
    }

    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vetores com dimensões diferentes: " + a.length + " e " + b.length);
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static double[] similarities(List<float[]> vectors) {
        double[] similarities = new double[Math.max(0, vectors.size() - 1)];
        for (int i = 0; i < similarities.length; i++) {
            similarities[i] = cosine(vectors.get(i), vectors.get(i + 1));
        }
        return similarities;
    }

    public static List<List<Integer>> group(List<float[]> vectors, int[] words, double tau, int maxWords) {
        if (vectors.size() != words.length) {
            throw new IllegalArgumentException("Número de vetores (" + vectors.size()
                    + ") diferente do número de contagens de palavras (" + words.length + ")");
        }
        List<List<Integer>> groups = new ArrayList<>();
        if (vectors.isEmpty()) {
            return groups;
        }
        List<Integer> current = new ArrayList<>();
        current.add(0);
        int currentWords = words[0];
        for (int next = 1; next < vectors.size(); next++) {
            int last = current.get(current.size() - 1);
            boolean similar = cosine(vectors.get(next), vectors.get(last)) >= tau;
            boolean fits = currentWords + words[next] <= maxWords;
            if (similar && fits) {
                current.add(next);
                currentWords += words[next];
            } else {
                groups.add(List.copyOf(current));
                current = new ArrayList<>();
                current.add(next);
                currentWords = words[next];
            }
        }
        groups.add(List.copyOf(current));
        return groups;
    }
}
