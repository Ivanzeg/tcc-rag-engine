package br.ufabc.tcc.rag.adapters.chunking;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Unit;
import java.util.ArrayList;
import java.util.List;

final class UnitChunks {

    private static final String SEPARATOR = "\n";

    private UnitChunks() {
    }

    static int wordCount(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }

    static int[] wordCounts(List<String> texts) {
        int[] words = new int[texts.size()];
        for (int i = 0; i < texts.size(); i++) {
            words[i] = wordCount(texts.get(i));
        }
        return words;
    }

    static String join(List<String> texts, List<Integer> indices) {
        List<String> parts = new ArrayList<>();
        for (int index : indices) {
            parts.add(texts.get(index));
        }
        return String.join(SEPARATOR, parts);
    }

    static List<List<Integer>> splitByWords(List<Integer> indices, int[] words, int maxWords) {
        List<List<Integer>> pieces = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        int currentWords = 0;
        for (int index : indices) {
            if (!current.isEmpty() && currentWords + words[index] > maxWords) {
                pieces.add(List.copyOf(current));
                current = new ArrayList<>();
                currentWords = 0;
            }
            current.add(index);
            currentWords += words[index];
        }
        if (!current.isEmpty()) {
            pieces.add(List.copyOf(current));
        }
        return pieces;
    }

    static List<Chunk> build(String strategy, List<Unit> units, List<List<Integer>> groups) {
        List<String> texts = units.stream().map(Unit::text).toList();
        List<Chunk> chunks = new ArrayList<>();
        for (List<Integer> group : groups) {
            String id = String.format("%s_c%04d", strategy, chunks.size() + 1);
            List<String> unitIds = group.stream().map(i -> units.get(i).id()).toList();
            chunks.add(new Chunk(id, strategy, join(texts, group), unitIds));
        }
        return chunks;
    }
}
