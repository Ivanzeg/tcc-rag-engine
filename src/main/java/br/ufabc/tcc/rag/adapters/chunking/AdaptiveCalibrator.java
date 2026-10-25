package br.ufabc.tcc.rag.adapters.chunking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AdaptiveCalibrator {

    public static final List<Integer> PERCENTILES = List.of(5, 10, 15, 20, 25, 30, 35, 40, 45, 50);

    public record Candidate(int percentile, double tau, int chunks, double meanChars, double meanWords,
            double distance) {
    }

    private AdaptiveCalibrator() {
    }

    public static List<Candidate> candidates(List<float[]> vectors, List<String> texts, int maxWords,
            double targetMeanChars) {
        if (vectors.size() != texts.size()) {
            throw new IllegalArgumentException("Número de vetores (" + vectors.size()
                    + ") diferente do número de textos (" + texts.size() + ")");
        }
        double[] similarities = AdaptiveGrouping.similarities(vectors);
        int[] words = UnitChunks.wordCounts(texts);
        List<Candidate> candidates = new ArrayList<>();
        for (int p : PERCENTILES) {
            double tau = Percentile.linear(similarities, p);
            List<List<Integer>> groups = AdaptiveGrouping.group(vectors, words, tau, maxWords);
            double totalChars = 0;
            double totalWords = 0;
            for (List<Integer> group : groups) {
                totalChars += UnitChunks.join(texts, group).length();
                for (int index : group) {
                    totalWords += words[index];
                }
            }
            double meanChars = totalChars / groups.size();
            candidates.add(new Candidate(p, tau, groups.size(), meanChars, totalWords / groups.size(),
                    Math.abs(meanChars - targetMeanChars)));
        }
        return candidates;
    }

    public static Candidate choose(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Não há candidatos para escolher");
        }
        List<Candidate> ordered = candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::percentile))
                .toList();
        Candidate best = ordered.get(0);
        for (Candidate candidate : ordered) {
            if (candidate.distance() < best.distance()) {
                best = candidate;
            }
        }
        return best;
    }
}
