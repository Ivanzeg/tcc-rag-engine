package br.ufabc.tcc.rag.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.Hit;
import br.ufabc.tcc.rag.core.domain.Stratum;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import br.ufabc.tcc.rag.core.port.ChatProvider;
import br.ufabc.tcc.rag.core.port.VectorIndex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AskCommandTest {

    @TempDir
    Path dir;

    private final Unit u1 = new Unit("u0001", 0, "CAPÍTULO I", "Art. 1o", UnitType.PROVISION, 0, 5, "texto");
    private final Unit u2 = new Unit("u0002", 1, "CAPÍTULO I", "Art. 2o", UnitType.PROVISION, 7, 12, "outro");
    private final Corpus corpus = new Corpus(List.of(u1, u2), "texto\n\noutro");
    private final List<GoldQuery> queries = List.of(
            new GoldQuery("F01", Stratum.FACTUAL, "Qual o objetivo da lei?", List.of("u0001")));
    private final List<Chunk> chunks = List.of(
            new Chunk("adaptive_c0001", "adaptive", "texto do primeiro chunk", List.of("u0001")),
            new Chunk("adaptive_c0002", "adaptive", "texto do segundo chunk", List.of("u0001", "u0002")));

    private final List<String> prompts = new ArrayList<>();
    private final ChatProvider chat = new ChatProvider() {
        @Override
        public String name() {
            return "falso";
        }

        @Override
        public String generate(String systemPrompt, String userPrompt) {
            prompts.add(systemPrompt);
            prompts.add(userPrompt);
            return "Resposta do falso.";
        }
    };

    private final VectorIndex index = new VectorIndex() {
        @Override
        public void reset(String strategy) {
            throw new AssertionError("o ask não pode recriar tabelas");
        }

        @Override
        public void addAll(List<Chunk> chunks, List<float[]> vectors) {
            throw new AssertionError("o ask não pode indexar");
        }

        @Override
        public List<Hit> search(float[] query, int k) {
            return List.of(
                    new Hit(1, "adaptive_c0002", 0.8, List.of("u0001", "u0002")),
                    new Hit(2, "adaptive_c0001", 0.7, List.of("u0001")));
        }
    };

    private AskCommand command(OptionalInt limit) {
        return new AskCommand(corpus, queries, q -> new float[] {1f}, index, chunks, chat,
                new AskCommand.Settings("adaptive", 5, "chat.provider=falso; chat.temperature=0.0", limit,
                        dir.resolve("agnosticism")));
    }

    @Test
    void gravaRelatorioComPerguntaFontesRespostaLatenciaEConfiguracao() throws IOException {
        Path file = command(OptionalInt.of(8192)).run("F01");

        assertEquals("falso_F01.md", file.getFileName().toString());
        String report = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(report.contains("Qual o objetivo da lei?"));
        assertTrue(report.contains("1. `adaptive_c0002` — Art. 1o, Art. 2o"));
        assertTrue(report.contains("2. `adaptive_c0001` — Art. 1o"));
        assertTrue(report.contains("Resposta do falso."));
        assertTrue(report.contains("## Latência da geração"));
        assertTrue(report.contains("chat.provider=falso; chat.temperature=0.0"));
    }

    @Test
    void promptEnviadoTemTrechosNumeradosEPergunta() {
        command(OptionalInt.empty()).run("F01");

        assertTrue(prompts.get(0).contains("apenas com base nos trechos"));
        assertTrue(prompts.get(1).contains("[1] (Art. 1o, Art. 2o)\ntexto do segundo chunk"));
        assertTrue(prompts.get(1).contains("[2] (Art. 1o)\ntexto do primeiro chunk"));
        assertTrue(prompts.get(1).endsWith("Pergunta: Qual o objetivo da lei?"));
    }

    @Test
    void consultaInexistenteFalhaEmPortugues() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> command(OptionalInt.empty()).run("X99"));
        assertTrue(erro.getMessage().contains("Consulta não encontrada"));
    }

    @Test
    void chunkForaDoArquivoFalhaEmPortugues() {
        AskCommand semChunks = new AskCommand(corpus, queries, q -> new float[] {1f}, index, List.of(), chat,
                new AskCommand.Settings("adaptive", 5, "x", OptionalInt.empty(), dir));
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> semChunks.run("F01"));
        assertTrue(erro.getMessage().contains("não consta"));
    }
}
