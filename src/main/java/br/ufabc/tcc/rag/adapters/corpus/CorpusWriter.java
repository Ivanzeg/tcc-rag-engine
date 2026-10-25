package br.ufabc.tcc.rag.adapters.corpus;

import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CorpusWriter {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> write(Path outputDir, CorpusBuild build, Path pdf, int startPage, int endPage) {
        Corpus corpus = build.corpus();
        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("lgpd.txt"), corpus.canonicalText(), StandardCharsets.UTF_8);
            List<String> jsonLines = new ArrayList<>();
            for (Unit unit : corpus.units()) {
                jsonLines.add(mapper.writeValueAsString(unit));
            }
            Files.write(outputDir.resolve("units.jsonl"), jsonLines, StandardCharsets.UTF_8);
            Map<String, Object> stats = buildStats(build, pdf, startPage, endPage);
            Files.writeString(outputDir.resolve("stats.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(stats) + "\n", StandardCharsets.UTF_8);
            return stats;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar os arquivos do corpus em " + outputDir, e);
        }
    }

    private Map<String, Object> buildStats(CorpusBuild build, Path pdf, int startPage, int endPage)
            throws IOException {
        Corpus corpus = build.corpus();
        CleaningCounters counters = build.counters();
        long provisions = corpus.units().stream().filter(u -> u.type() == UnitType.PROVISION).count();
        long headings = corpus.units().size() - provisions;
        long articles = corpus.units().stream()
                .filter(u -> u.type() == UnitType.PROVISION && LegalUnitSegmenter.ARTICLE.matcher(u.text()).find())
                .count();
        long chapters = corpus.units().stream()
                .filter(u -> u.type() == UnitType.HEADING
                        && u.text().toUpperCase(Locale.ROOT).startsWith("CAPÍTULO"))
                .count();
        String text = corpus.canonicalText();
        long words = text.isBlank() ? 0 : text.trim().split("\\s+").length;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("units", corpus.units().size());
        stats.put("provisions", provisions);
        stats.put("headings", headings);
        stats.put("articles", articles);
        stats.put("chapters", chapters);
        stats.put("words", words);
        stats.put("characters", text.length());
        stats.put("pdfStartPage", startPage);
        stats.put("pdfEndPage", endPage);
        stats.put("pdfSha256", sha256(Files.readAllBytes(pdf)));
        stats.put("corpusSha256", sha256(text.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> cleaning = new LinkedHashMap<>();
        cleaning.put("headerFooterLinesRemoved", counters.headerFooterLines);
        cleaning.put("standaloneNumberLinesRemoved", counters.standaloneNumberLines);
        cleaning.put("separatorLinesRemoved", counters.separatorLines);
        cleaning.put("hyphenJoins", counters.hyphenJoins);
        cleaning.put("hyphenRepeatJoins", counters.hyphenRepeatJoins);
        cleaning.put("annotationsRemoved", counters.annotations);
        cleaning.put("vetoedDiscarded", counters.vetoed);
        cleaning.put("emptiedByAnnotation", counters.emptiedByAnnotation);
        cleaning.put("preambleLinesDiscarded", counters.preambleLines);
        cleaning.put("closingLinesDiscarded", counters.closingLines);
        cleaning.put("editorNoteLinesDiscarded", counters.editorNoteLines);
        stats.put("cleaning", cleaning);
        return stats;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
