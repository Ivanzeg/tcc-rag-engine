package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.core.domain.Chunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ChunkLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Chunk> load(Path chunksDir, String strategy) {
        Path file = chunksDir.resolve(strategy + ".jsonl");
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Arquivo de chunks não encontrado: " + file
                    + ". Rode run-all ou evaluate " + strategy + " antes do ask.");
        }
        try {
            List<Chunk> chunks = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    chunks.add(mapper.readValue(line, Chunk.class));
                }
            }
            return chunks;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + file, e);
        }
    }
}
