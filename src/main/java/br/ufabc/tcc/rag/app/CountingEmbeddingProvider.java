package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.core.port.EmbeddingProvider;
import java.util.List;

public final class CountingEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingProvider delegate;
    private int submittedTexts;

    public CountingEmbeddingProvider(EmbeddingProvider delegate) {
        this.delegate = delegate;
    }

    public int submittedTexts() {
        return submittedTexts;
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    @Override
    public float[] embed(String text) {
        submittedTexts++;
        return delegate.embed(text);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        submittedTexts += texts.size();
        return delegate.embedAll(texts);
    }
}
