package br.ufabc.tcc.rag.adapters.chunking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class TfIdf {

    private static final String TOKEN_SEPARATOR = "[^\\p{L}\\p{N}]+";

    private final Set<String> stopwords;

    TfIdf(Set<String> stopwords) {
        this.stopwords = stopwords;
    }

    List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split(TOKEN_SEPARATOR)) {
            if (!token.isEmpty() && !stopwords.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    List<double[]> vectorize(List<String> documents) {
        List<Map<String, Integer>> termCounts = new ArrayList<>();
        TreeMap<String, Integer> documentFrequency = new TreeMap<>();
        for (String document : documents) {
            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokenize(document)) {
                counts.merge(token, 1, Integer::sum);
            }
            termCounts.add(counts);
            for (String term : counts.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        int n = documents.size();
        Map<String, Integer> columns = new HashMap<>();
        double[] idf = new double[documentFrequency.size()];
        int column = 0;
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            columns.put(entry.getKey(), column);
            idf[column] = Math.log((1.0 + n) / (1.0 + entry.getValue())) + 1.0;
            column++;
        }

        List<double[]> vectors = new ArrayList<>();
        for (Map<String, Integer> counts : termCounts) {
            double[] vector = new double[idf.length];
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                int c = columns.get(entry.getKey());
                vector[c] = entry.getValue() * idf[c];
            }
            vectors.add(normalize(vector));
        }
        return vectors;
    }

    private static double[] normalize(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return vector;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
