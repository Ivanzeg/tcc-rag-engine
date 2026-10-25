package br.ufabc.tcc.rag.adapters.chunking;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.port.Chunker;
import br.ufabc.tcc.rag.core.port.EmbeddingProvider;
import java.util.List;
import java.util.Locale;

public final class AdaptiveChunker implements Chunker {

    private static final String NAME = "adaptive";

    private final EmbeddingProvider embeddings;
    private final double tau;
    private final int maxWords;

    public AdaptiveChunker(EmbeddingProvider embeddings, double tau, int maxWords) {
        this.embeddings = embeddings;
        this.tau = tau;
        this.maxWords = maxWords;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Chunk> chunk(Corpus corpus) {
        List<Unit> units = corpus.units();
        List<String> texts = units.stream().map(Unit::text).toList();
        List<float[]> vectors = embeddings.embedAll(texts);
        List<List<Integer>> groups = AdaptiveGrouping.group(vectors, UnitChunks.wordCounts(texts), tau, maxWords);
        System.out.println(String.format(Locale.ROOT, "Adaptativo: τ* = %.6f, teto de %d palavras, %d chunks",
                tau, maxWords, groups.size()));
        return UnitChunks.build(NAME, units, groups);
    }
}
