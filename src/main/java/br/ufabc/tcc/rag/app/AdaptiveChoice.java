package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.chunking.AdaptiveCalibrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record AdaptiveChoice(int percentile, double tau, String percentileMethod, double fixedMeanChars,
        int fixedChunks, String fixedChunksSha256, int maxWords, String embeddingModel, String corpusSha256,
        int units, List<AdaptiveCalibrator.Candidate> candidates) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static AdaptiveChoice read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Não encontrei " + file
                    + ". Rode calibrate-adaptive antes de usar a estratégia adaptive.");
        }
        try {
            return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), AdaptiveChoice.class);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + file, e);
        }
    }

    public void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + file, e);
        }
    }
}
