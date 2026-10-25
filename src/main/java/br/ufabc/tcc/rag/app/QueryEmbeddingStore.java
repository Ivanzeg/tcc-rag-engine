package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.port.EmbeddingProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QueryEmbeddingStore {

    private final Path file;
    private final String modelName;
    private final EmbeddingProvider embeddings;
    private final ObjectMapper mapper = new ObjectMapper();

    public QueryEmbeddingStore(Path file, String modelName, EmbeddingProvider embeddings) {
        this.file = file;
        this.modelName = modelName;
        this.embeddings = embeddings;
    }

    public QueryEmbeddingStore(Path file, String modelName) {
        this(file, modelName, null);
    }

    public float[] read(GoldQuery query) {
        Entry entry = readEntries().get(query.id());
        if (entry == null) {
            throw new IllegalStateException("Não há vetor gravado para " + query.id() + " em " + file
                    + ". Rode evaluate ou run-all antes do ask.");
        }
        if (!entry.questionSha256().equals(Sha256.hex(query.question()))) {
            throw new IllegalStateException("A pergunta de " + query.id() + " mudou desde que o vetor foi gravado em "
                    + file + ". Os vetores das consultas são fixos no experimento");
        }
        return entry.vector();
    }

    public Map<String, float[]> load(List<GoldQuery> queries) {
        Map<String, Entry> entries = readEntries();
        List<GoldQuery> missing = new ArrayList<>();
        for (GoldQuery query : queries) {
            Entry entry = entries.get(query.id());
            if (entry == null) {
                missing.add(query);
            } else if (!entry.questionSha256().equals(Sha256.hex(query.question()))) {
                throw new IllegalStateException("A pergunta de " + query.id() + " mudou desde que o vetor foi gravado em "
                        + file + ". Os vetores das consultas são fixos no experimento");
            }
        }
        if (!missing.isEmpty()) {
            List<float[]> computed = embeddings.embedAll(missing.stream().map(GoldQuery::question).toList());
            for (int i = 0; i < missing.size(); i++) {
                GoldQuery query = missing.get(i);
                entries.put(query.id(), new Entry(Sha256.hex(query.question()), computed.get(i)));
            }
            writeEntries(entries);
            System.out.println("Vetores de consulta calculados e gravados: " + missing.size() + " em " + file);
        } else {
            System.out.println("Vetores de consulta reaproveitados de " + file);
        }
        Map<String, float[]> vectors = new LinkedHashMap<>();
        for (GoldQuery query : queries) {
            vectors.put(query.id(), entries.get(query.id()).vector());
        }
        return vectors;
    }

    private Map<String, Entry> readEntries() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            Stored stored = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
            if (!modelName.equals(stored.model())) {
                throw new IllegalStateException("O modelo gravado em " + file + " é " + stored.model()
                        + ", mas openai.embedding.model é " + modelName);
            }
            return new LinkedHashMap<>(stored.entries());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + file, e);
        }
    }

    private void writeEntries(Map<String, Entry> entries) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(new Stored(modelName, entries)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + file, e);
        }
    }

    private record Entry(String questionSha256, float[] vector) {
    }

    private record Stored(String model, Map<String, Entry> entries) {
    }
}
