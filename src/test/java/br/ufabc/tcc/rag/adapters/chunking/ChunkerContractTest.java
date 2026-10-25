package br.ufabc.tcc.rag.adapters.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufabc.tcc.rag.app.CorpusLoader;
import br.ufabc.tcc.rag.core.domain.Chunk;
import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import br.ufabc.tcc.rag.core.port.Chunker;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkerContractTest {

    private static Corpus corpus;
    private static Chunker chunker;
    private static List<Chunk> chunks;

    @BeforeAll
    static void chunkCorpus() {
        corpus = new CorpusLoader().load(Path.of("data", "corpus"));
        chunker = new FixedChunker();
        chunks = chunker.chunk(corpus);
    }

    @Test
    void todaUnidadeProvisionApareceEmPeloMenosUmChunk() {
        Set<String> covered = new HashSet<>();
        chunks.forEach(chunk -> covered.addAll(chunk.unitIds()));
        List<String> uncovered = corpus.units().stream()
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
    void textoDoChunkEstaNoTextoCanonicoComEspacosNormalizados() {
        String canonical = collapseWhitespace(corpus.canonicalText());
        for (Chunk chunk : chunks) {
            assertFalse(chunk.text().isBlank(), "Chunk vazio: " + chunk.id());
            assertTrue(canonical.contains(collapseWhitespace(chunk.text())),
                    "Texto do chunk não consta no texto canônico, nem com espaços normalizados: " + chunk.id());
        }
    }

    private static String collapseWhitespace(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    @Test
    void chunksFixosNaoPassamDoTamanhoMaximo() {
        for (Chunk chunk : chunks) {
            assertTrue(chunk.text().length() <= 1000,
                    "Chunk com " + chunk.text().length() + " caracteres: " + chunk.id());
        }
    }
}
