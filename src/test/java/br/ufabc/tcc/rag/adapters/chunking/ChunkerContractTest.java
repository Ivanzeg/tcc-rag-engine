package br.ufabc.tcc.rag.adapters.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.ufabc.tcc.rag.adapters.Sha256;
import br.ufabc.tcc.rag.adapters.embedding.OpenAiEmbeddingProvider;
import br.ufabc.tcc.rag.app.AdaptiveChoice;
import br.ufabc.tcc.rag.app.Config;
import br.ufabc.tcc.rag.app.CorpusLoader;
import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import br.ufabc.tcc.rag.core.port.Chunker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

class ChunkerContractTest {

    private static final Corpus CORPUS = new CorpusLoader().load(Path.of("data", "corpus"));
    private static final Config PROTOCOL = Config.load(Path.of("config.example.properties"));
    private static final Path ADAPTIVE_CHOICE_FILE = Path.of("data", "calibration", "adaptive_choice.json");
    private static final Path EMBEDDING_CACHE_DIR = Path.of("data", "cache", "embeddings");
    private static final String TEST_API_KEY = "sem-chave-nos-testes";

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    abstract static class Contract {

        Chunker chunker;
        List<Chunk> chunks;

        abstract Chunker newChunker();

        @BeforeAll
        void chunkCorpus() {
            chunker = newChunker();
            chunks = chunker.chunk(CORPUS);
        }

        @Test
        void todaUnidadeProvisionApareceEmPeloMenosUmChunk() {
            Set<String> covered = new HashSet<>();
            chunks.forEach(chunk -> covered.addAll(chunk.unitIds()));
            List<String> uncovered = CORPUS.units().stream()
                    .filter(unit -> unit.type() == UnitType.PROVISION)
                    .map(Unit::id)
                    .filter(id -> !covered.contains(id))
                    .toList();
            assertTrue(uncovered.isEmpty(), "Unidades PROVISION sem chunk (" + uncovered.size() + "): " + uncovered);
        }

        @Test
        void idsSeguemOFormatoDaEstrategia() {
            assertFalse(chunks.isEmpty(), "Nenhum chunk gerado");
            for (int i = 0; i < chunks.size(); i++) {
                String expected = String.format("%s_c%04d", chunker.name(), i + 1);
                assertEquals(expected, chunks.get(i).id(), "ID inesperado na posição " + i);
                assertEquals(chunker.name(), chunks.get(i).strategy(), "Estratégia inesperada em " + expected);
            }
        }

        @Test
        void nenhumChunkSemUnidades() {
            for (Chunk chunk : chunks) {
                assertFalse(chunk.unitIds().isEmpty(), "Chunk sem unitIds: " + chunk.id());
            }
        }

        @Test
        void duasExecucoesGeramChunksIdenticos() {
            List<Chunk> again = newChunker().chunk(CORPUS);
            assertEquals(chunks.size(), again.size(), "Número de chunks mudou entre duas execuções");
            for (int i = 0; i < chunks.size(); i++) {
                assertEquals(chunks.get(i), again.get(i), "Chunk diferente entre duas execuções: " + chunks.get(i).id());
            }
        }
    }

    abstract static class UnitGroupContract extends Contract {

        abstract int maxWords();

        @Test
        void cadaUnidadeApareceEmExatamenteUmChunk() {
            Map<String, Integer> occurrences = new HashMap<>();
            chunks.forEach(chunk -> chunk.unitIds().forEach(id -> occurrences.merge(id, 1, Integer::sum)));
            List<String> wrong = CORPUS.units().stream()
                    .map(Unit::id)
                    .filter(id -> occurrences.getOrDefault(id, 0) != 1)
                    .toList();
            assertTrue(wrong.isEmpty(), "Unidades que não aparecem exatamente uma vez: " + wrong);
            assertEquals(CORPUS.units().size(), occurrences.size(), "unitIds fora do corpus");
        }

        @Test
        void textoDoChunkEhAJuncaoDasUnidades() {
            Map<String, Unit> unitsById = CORPUS.units().stream()
                    .collect(Collectors.toMap(Unit::id, Function.identity()));
            for (Chunk chunk : chunks) {
                String expected = chunk.unitIds().stream()
                        .map(id -> unitsById.get(id).text())
                        .collect(Collectors.joining("\n"));
                assertEquals(expected, chunk.text(), "Texto do chunk não é a junção das unidades: " + chunk.id());
            }
        }

        @Test
        void chunksRespeitamOTetoDePalavras() {
            Map<String, Unit> unitsById = CORPUS.units().stream()
                    .collect(Collectors.toMap(Unit::id, Function.identity()));
            List<Chunk> oversized = chunks.stream()
                    .filter(chunk -> UnitChunks.wordCount(chunk.text()) > maxWords())
                    .toList();
            List<String> solitary = oversized.stream()
                    .filter(chunk -> chunk.unitIds().size() == 1)
                    .map(chunk -> chunk.unitIds().get(0) + " ("
                            + UnitChunks.wordCount(unitsById.get(chunk.unitIds().get(0)).text()) + " palavras)")
                    .toList();
            System.out.println(chunker.name() + ": unidades sozinhas acima do teto de " + maxWords() + " palavras: "
                    + (solitary.isEmpty() ? "nenhuma" : solitary));
            List<String> violations = oversized.stream()
                    .filter(chunk -> chunk.unitIds().size() > 1)
                    .map(chunk -> chunk.id() + " (" + UnitChunks.wordCount(chunk.text()) + " palavras)")
                    .toList();
            assertTrue(violations.isEmpty(), "Chunks com mais de uma unidade acima do teto de " + maxWords()
                    + " palavras: " + violations);
        }
    }

    @Nested
    class Fixed extends Contract {

        @Override
        Chunker newChunker() {
            return new FixedChunker();
        }

        @Test
        void textoDoChunkEstaNoTextoCanonicoComEspacosNormalizados() {
            assertChunksInCanonicalText(chunks);
        }

        @Test
        void chunksFixosNaoPassamDoTamanhoMaximo() {
            for (Chunk chunk : chunks) {
                assertTrue(chunk.text().length() <= 1000,
                        "Chunk com " + chunk.text().length() + " caracteres: " + chunk.id());
            }
        }
    }

    @Nested
    class Semantic extends UnitGroupContract {

        @Override
        Chunker newChunker() {
            return new SemanticChunker(PROTOCOL.semanticK(), PROTOCOL.semanticMaxWords());
        }

        @Override
        int maxWords() {
            return PROTOCOL.semanticMaxWords();
        }
    }

    @Nested
    class Adaptive extends UnitGroupContract {

        @Override
        Chunker newChunker() {
            assumeTrue(Files.isRegularFile(ADAPTIVE_CHOICE_FILE),
                    "Contrato do adaptive pulado: falta " + ADAPTIVE_CHOICE_FILE + " (rode calibrate-adaptive)");
            AdaptiveChoice choice = AdaptiveChoice.read(ADAPTIVE_CHOICE_FILE);
            OpenAiEmbeddingProvider cacheOnly =
                    new OpenAiEmbeddingProvider(TEST_API_KEY, choice.embeddingModel(), EMBEDDING_CACHE_DIR);
            long missing = CORPUS.units().stream().filter(unit -> !cacheOnly.isCached(unit.text())).count();
            assumeTrue(missing == 0, "Contrato do adaptive pulado: " + missing + " unidades sem vetor no cache");
            assertEquals(Sha256.hex(CORPUS.canonicalText()), choice.corpusSha256(),
                    "A calibração foi feita sobre outro corpus");
            assertEquals(PROTOCOL.adaptiveMaxWords(), choice.maxWords(), "A calibração usou outro teto de palavras");
            return new AdaptiveChunker(cacheOnly, choice.tau(), choice.maxWords());
        }

        @Override
        int maxWords() {
            return PROTOCOL.adaptiveMaxWords();
        }

        @Test
        void textoDoChunkEstaNoTextoCanonicoComEspacosNormalizados() {
            assertChunksInCanonicalText(chunks);
        }

        @Test
        void unidadesDeCadaChunkSaoConsecutivas() {
            Map<String, Integer> indexById = CORPUS.units().stream()
                    .collect(Collectors.toMap(Unit::id, Unit::index));
            for (Chunk chunk : chunks) {
                for (int i = 1; i < chunk.unitIds().size(); i++) {
                    assertEquals(indexById.get(chunk.unitIds().get(i - 1)) + 1, indexById.get(chunk.unitIds().get(i)),
                            "Unidades não consecutivas no chunk " + chunk.id());
                }
            }
        }
    }

    private static void assertChunksInCanonicalText(List<Chunk> chunks) {
        String canonical = collapseWhitespace(CORPUS.canonicalText());
        for (Chunk chunk : chunks) {
            assertFalse(chunk.text().isBlank(), "Chunk vazio: " + chunk.id());
            assertTrue(canonical.contains(collapseWhitespace(chunk.text())),
                    "Texto do chunk não consta no texto canônico, nem com espaços normalizados: " + chunk.id());
        }
    }

    private static String collapseWhitespace(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }
}
