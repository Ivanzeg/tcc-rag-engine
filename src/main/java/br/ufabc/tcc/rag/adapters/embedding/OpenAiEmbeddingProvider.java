package br.ufabc.tcc.rag.adapters.embedding;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.core.port.EmbeddingProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRIES = 3;

    private final EmbeddingModel model;
    private final String modelName;
    private final Path cacheDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiEmbeddingProvider(String apiKey, String modelName, Path cacheDir) {
        this.modelName = modelName;
        this.cacheDir = cacheDir;
        this.model = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
    }

    @Override
    public int dimension() {
        return model.dimension();
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        float[][] vectors = new float[texts.size()][];
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            vectors[i] = readCache(texts.get(i));
            if (vectors[i] == null) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            List<TextSegment> segments = missing.stream()
                    .map(i -> TextSegment.from(texts.get(i)))
                    .toList();
            List<Embedding> computed = model.embedAll(segments).content();
            if (computed.size() != missing.size()) {
                throw new IllegalStateException("A API devolveu " + computed.size() + " embeddings para "
                        + missing.size() + " textos");
            }
            for (int j = 0; j < missing.size(); j++) {
                int index = missing.get(j);
                vectors[index] = computed.get(j).vector();
                writeCache(texts.get(index), vectors[index]);
            }
        }
        System.out.println("Embeddings (" + modelName + "): " + (texts.size() - missing.size())
                + " do cache, " + missing.size() + " calculados pela API");
        return List.of(vectors);
    }

    private Path cacheFile(String text) {
        return cacheDir.resolve(Sha256.hex(text) + ".json");
    }

    private float[] readCache(String text) {
        Path file = cacheFile(text);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CacheEntry entry = mapper.readValue(Files.readString(file, StandardCharsets.UTF_8), CacheEntry.class);
            if (!modelName.equals(entry.model()) || entry.vector() == null) {
                return null;
            }
            return entry.vector();
        } catch (IOException e) {
            return null;
        }
    }

    private void writeCache(String text, float[] vector) {
        Path file = cacheFile(text);
        Path temporary = cacheDir.resolve(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(cacheDir);
            Files.writeString(temporary, mapper.writeValueAsString(new CacheEntry(modelName, vector)),
                    StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar o cache de embeddings em " + file, e);
        }
    }

    private record CacheEntry(String model, float[] vector) {
    }
}
