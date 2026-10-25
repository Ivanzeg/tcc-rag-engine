package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.app.AskPromptBuilder.Source;
import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.Hit;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import br.ufabc.tcc.rag.core.port.ChatProvider;
import br.ufabc.tcc.rag.core.port.VectorIndex;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AskCommand {

    public record Settings(String strategy, int k, String configLine, OptionalInt contextLimit, Path outputDir) {
    }

    private final Corpus corpus;
    private final List<GoldQuery> queries;
    private final Function<GoldQuery, float[]> queryVectors;
    private final VectorIndex index;
    private final List<Chunk> chunks;
    private final ChatProvider chat;
    private final Settings settings;

    public AskCommand(Corpus corpus, List<GoldQuery> queries, Function<GoldQuery, float[]> queryVectors,
            VectorIndex index, List<Chunk> chunks, ChatProvider chat, Settings settings) {
        this.corpus = corpus;
        this.queries = queries;
        this.queryVectors = queryVectors;
        this.index = index;
        this.chunks = chunks;
        this.chat = chat;
        this.settings = settings;
    }

    public Path run(String queryId) {
        GoldQuery query = queries.stream()
                .filter(q -> q.id().equals(queryId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Consulta não encontrada em queries_resolved.json: "
                        + queryId));
        List<Hit> hits = index.search(queryVectors.apply(query), settings.k());
        List<Source> sources = sourcesOf(hits);

        String systemPrompt = AskPromptBuilder.systemPrompt();
        String userPrompt = AskPromptBuilder.userPrompt(sources, query.question());
        int estimatedTokens = AskPromptBuilder.estimateTokens(systemPrompt, userPrompt);
        System.out.println("Prompt estimado: " + estimatedTokens + " tokens (caracteres ÷ 3)");
        if (settings.contextLimit().isPresent()
                && AskPromptBuilder.nearContextLimit(estimatedTokens, settings.contextLimit().getAsInt())) {
            System.out.println("Aviso: o prompt estimado (" + estimatedTokens + " tokens) passa de 80% de num_ctx ("
                    + settings.contextLimit().getAsInt() + "). O excesso pode ser cortado sem aviso pelo Ollama.");
        }

        System.out.println("Gerando resposta com " + chat.name() + "...");
        long startedAt = System.nanoTime();
        String answer = chat.generate(systemPrompt, userPrompt);
        double latencyMillis = (System.nanoTime() - startedAt) / 1_000_000.0;
        System.out.println(String.format(Locale.ROOT, "Latência da geração: %.0f ms", latencyMillis));

        Path file = new AskReportWriter(settings.outputDir()).write(new AskReportWriter.Report(
                chat.name(), query.id(), query.question(), settings.strategy(), sources, answer, latencyMillis,
                settings.configLine(), estimatedTokens));
        System.out.println("Relatório gravado em " + file);
        return file;
    }

    private List<Source> sourcesOf(List<Hit> hits) {
        Map<String, Chunk> chunksById = chunks.stream().collect(Collectors.toMap(Chunk::id, Function.identity()));
        Map<String, Unit> unitsById = corpus.units().stream().collect(Collectors.toMap(Unit::id, Function.identity()));
        List<Source> sources = new ArrayList<>();
        for (Hit hit : hits) {
            Chunk chunk = chunksById.get(hit.chunkId());
            if (chunk == null) {
                throw new IllegalStateException("O chunk " + hit.chunkId() + " veio do banco, mas não consta em"
                        + " data/chunks/" + settings.strategy() + ".jsonl. Reindexe com run-all.");
            }
            sources.add(new Source(hit.rank(), hit.chunkId(), articlesOf(hit.unitIds(), unitsById), chunk.text()));
        }
        return sources;
    }

    private static String articlesOf(List<String> unitIds, Map<String, Unit> unitsById) {
        Set<String> articles = new LinkedHashSet<>();
        for (String unitId : unitIds) {
            Unit unit = unitsById.get(unitId);
            if (unit != null && unit.type() == UnitType.PROVISION && !unit.article().isBlank()) {
                articles.add(unit.article());
            }
        }
        return articles.isEmpty() ? "sem artigo" : String.join(", ", articles);
    }
}
