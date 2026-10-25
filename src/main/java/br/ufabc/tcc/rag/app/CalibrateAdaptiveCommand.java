package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.adapters.chunking.AdaptiveCalibrator;
import br.ufabc.tcc.rag.adapters.chunking.AdaptiveCalibrator.Candidate;
import br.ufabc.tcc.rag.adapters.chunking.AdaptiveGrouping;
import br.ufabc.tcc.rag.adapters.chunking.Percentile;
import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CalibrateAdaptiveCommand {

    private static final Path FIXED_CHUNKS_FILE = ExperimentServices.CHUNKS_DIR.resolve("fixed.jsonl");
    private static final Path SIMILARITIES_FILE =
            ExperimentServices.CALIBRATION_DIR.resolve("adaptive_similarities.csv");

    private final ExperimentServices services;
    private final ObjectMapper mapper = new ObjectMapper();

    public CalibrateAdaptiveCommand(ExperimentServices services) {
        this.services = services;
    }

    public void run() {
        Config config = services.config();
        Corpus corpus = services.corpus();
        int maxWords = config.adaptiveMaxWords();
        FixedReference fixed = readFixed();

        List<Unit> units = corpus.units();
        List<String> texts = units.stream().map(Unit::text).toList();
        List<float[]> vectors = services.embeddings().embedAll(texts);
        double[] similarities = AdaptiveGrouping.similarities(vectors);

        List<Candidate> candidates = AdaptiveCalibrator.candidates(vectors, texts, maxWords, fixed.meanChars());
        Candidate chosen = AdaptiveCalibrator.choose(candidates);

        writeSimilarities(units, similarities);
        AdaptiveChoice choice = new AdaptiveChoice(chosen.percentile(), chosen.tau(), Percentile.METHOD,
                fixed.meanChars(), fixed.chunks(), fixed.sha256(), maxWords, config.openAiEmbeddingModel(),
                Sha256.hex(corpus.canonicalText()), units.size(), candidates);
        choice.write(ExperimentServices.ADAPTIVE_CHOICE_FILE);

        printReport(units.size(), similarities, fixed, candidates, chosen, maxWords);
    }

    private FixedReference readFixed() {
        if (!Files.isRegularFile(FIXED_CHUNKS_FILE)) {
            throw new IllegalStateException("Não encontrei " + FIXED_CHUNKS_FILE
                    + ". Rode evaluate fixed antes da calibração.");
        }
        try {
            String content = Files.readString(FIXED_CHUNKS_FILE, StandardCharsets.UTF_8);
            long totalChars = 0;
            int count = 0;
            for (String line : content.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                Chunk chunk = mapper.readValue(line, Chunk.class);
                if (!"fixed".equals(chunk.strategy())) {
                    throw new IllegalStateException("Chunk de outra estratégia em " + FIXED_CHUNKS_FILE + ": "
                            + chunk.id());
                }
                totalChars += chunk.text().length();
                count++;
            }
            if (count == 0) {
                throw new IllegalStateException("Nenhum chunk em " + FIXED_CHUNKS_FILE);
            }
            return new FixedReference(count, (double) totalChars / count, Sha256.hex(content));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + FIXED_CHUNKS_FILE, e);
        }
    }

    private void writeSimilarities(List<Unit> units, double[] similarities) {
        List<String> lines = new ArrayList<>();
        lines.add("i,unit_id,next_unit_id,similarity");
        for (int i = 0; i < similarities.length; i++) {
            lines.add(String.join(",", String.valueOf(i + 1), units.get(i).id(), units.get(i + 1).id(),
                    String.format(Locale.ROOT, "%.10f", similarities[i])));
        }
        try {
            Files.createDirectories(SIMILARITIES_FILE.getParent());
            Files.write(SIMILARITIES_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + SIMILARITIES_FILE, e);
        }
    }

    private static void printReport(int unitCount, double[] similarities, FixedReference fixed,
            List<Candidate> candidates, Candidate chosen, int maxWords) {
        double[] sorted = similarities.clone();
        Arrays.sort(sorted);
        System.out.println();
        System.out.println(String.format(Locale.ROOT,
                "Similaridades consecutivas: %d valores (%d unidades). mínimo=%.4f mediana=%.4f máximo=%.4f",
                similarities.length, unitCount, sorted[0], Percentile.linear(similarities, 50),
                sorted[sorted.length - 1]));
        System.out.println("Percentil: " + Percentile.METHOD);
        System.out.println(String.format(Locale.ROOT,
                "Alvo: média de %.2f caracteres nos %d chunks de %s. Teto: %d palavras.",
                fixed.meanChars(), fixed.chunks(), FIXED_CHUNKS_FILE, maxWords));
        System.out.println();
        System.out.println("     p       τ_p  chunks  média_car  média_pal   |dif|");
        for (Candidate c : candidates) {
            System.out.println(String.format(Locale.ROOT, "%s %3d  %.6f  %6d  %9.2f  %9.2f  %7.2f",
                    c == chosen ? "*" : " ", c.percentile(), c.tau(), c.chunks(), c.meanChars(), c.meanWords(),
                    c.distance()));
        }
        System.out.println();
        System.out.println(String.format(Locale.ROOT, "Escolha: p* = %d, τ* = %.6f (%d chunks, média de %.2f caracteres)",
                chosen.percentile(), chosen.tau(), chosen.chunks(), chosen.meanChars()));
        int first = AdaptiveCalibrator.PERCENTILES.get(0);
        int last = AdaptiveCalibrator.PERCENTILES.get(AdaptiveCalibrator.PERCENTILES.size() - 1);
        if (chosen.percentile() == first || chosen.percentile() == last) {
            System.out.println("Atenção: p* está no limite do intervalo de candidatos (" + first + " a " + last + ").");
        }
        System.out.println("Gravado em " + SIMILARITIES_FILE + " e " + ExperimentServices.ADAPTIVE_CHOICE_FILE);
    }

    private record FixedReference(int chunks, double meanChars, String sha256) {
    }
}
