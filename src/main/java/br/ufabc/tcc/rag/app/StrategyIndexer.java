package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.port.Chunker;
import br.ufabc.tcc.rag.core.port.EmbeddingProvider;
import br.ufabc.tcc.rag.core.port.VectorIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StrategyIndexer {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Chunk> index(Chunker chunker, Corpus corpus, EmbeddingProvider embeddings, VectorIndex index,
            Path chunksDir) {
        long startedAt = System.nanoTime();
        String strategy = chunker.name();
        List<Chunk> chunks = chunker.chunk(corpus);
        System.out.println("Estratégia " + strategy + ": " + chunks.size() + " chunks gerados");
        writeChunks(chunksDir.resolve(strategy + ".jsonl"), chunks);
        List<float[]> vectors = embeddings.embedAll(chunks.stream().map(Chunk::text).toList());
        index.reset(strategy);
        index.addAll(chunks, vectors);
        double seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        System.out.println(String.format(Locale.ROOT, "Estratégia %s indexada em %.1f s (tabela chunks_%s, índice HNSW)",
                strategy, seconds, strategy));
        return chunks;
    }

    private void writeChunks(Path file, List<Chunk> chunks) {
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = new ArrayList<>();
            for (Chunk chunk : chunks) {
                lines.add(mapper.writeValueAsString(chunk));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar os chunks em " + file, e);
        }
    }
}
