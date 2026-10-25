package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.app.AskPromptBuilder.Source;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class AskReportWriter {

    public record Report(String provider, String queryId, String question, String strategy, List<Source> sources,
            String answer, double latencyMillis, String configLine, int estimatedPromptTokens) {
    }

    private final Path directory;

    public AskReportWriter(Path directory) {
        this.directory = directory;
    }

    public Path write(Report report) {
        Path file = directory.resolve(report.provider() + "_" + report.queryId() + ".md");
        try {
            Files.createDirectories(directory);
            Files.writeString(file, format(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + file, e);
        }
        return file;
    }

    static String format(Report report) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(report.queryId()).append(" — ").append(report.provider()).append("\n\n");
        out.append("## Pergunta\n\n").append(report.question()).append("\n\n");
        out.append("## Fontes (estratégia ").append(report.strategy()).append(")\n\n");
        for (Source source : report.sources()) {
            out.append(source.number()).append(". `").append(source.chunkId()).append("` — ")
                    .append(source.articles()).append('\n');
        }
        out.append("\n## Resposta\n\n").append(report.answer()).append("\n\n");
        out.append("## Latência da geração\n\n")
                .append(String.format(Locale.ROOT, "%.0f ms", report.latencyMillis())).append("\n\n");
        out.append("## Prompt\n\n").append("Tamanho estimado: ").append(report.estimatedPromptTokens())
                .append(" tokens (caracteres ÷ 3)\n\n");
        out.append("## Configuração\n\n`").append(report.configLine()).append("`\n");
        return out.toString();
    }
}
