package br.ufabc.tcc.rag.adapters.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufabc.tcc.rag.core.domain.GoldQuery;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenResolverTest {

    @TempDir
    Path dir;

    private Path units;
    private Path queries;
    private Path output;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        units = dir.resolve("units.jsonl");
        queries = dir.resolve("queries.json");
        output = dir.resolve("saida").resolve("queries_resolved.json");
        List<Unit> list = List.of(
                new Unit("u0001", 0, "CAPÍTULO I", "", UnitType.HEADING, 0, 20, "CAPÍTULO I - Dados sensíveis"),
                new Unit("u0002", 1, "CAPÍTULO I", "Art. 1o", UnitType.PROVISION, 22, 60,
                        "Art. 1o Esta Lei protege o “dado pessoal” – de qualquer pessoa."),
                new Unit("u0003", 2, "CAPÍTULO I", "Art. 2o", UnitType.PROVISION, 62, 100,
                        "Art. 2o O tratamento de dados exige consentimento do titular."),
                new Unit("u0004", 3, "CAPÍTULO I", "Art. 3o", UnitType.PROVISION, 102, 140,
                        "Art. 3o O tratamento de dados exige base legal adequada."));
        StringBuilder jsonl = new StringBuilder();
        for (Unit unit : list) {
            jsonl.append(mapper.writeValueAsString(unit)).append('\n');
        }
        Files.writeString(units, jsonl.toString(), StandardCharsets.UTF_8);
    }

    private void writeQuery(String evidence) throws IOException {
        String json = "[{\"id\":\"F01\",\"stratum\":\"FACTUAL\",\"question\":\"pergunta\","
                + "\"evidence\":[" + mapper.writeValueAsString(evidence) + "],\"note\":\"nota\"}]";
        Files.writeString(queries, json, StandardCharsets.UTF_8);
    }

    private List<GoldQuery> run() {
        return new GoldenResolver().resolve(queries, units, output,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @Test
    void evidenciaEmExatamenteUmaUnidadeResolve() throws IOException {
        writeQuery("consentimento do titular");
        List<GoldQuery> result = run();
        assertEquals(List.of("u0003"), result.get(0).evidenceUnitIds(), "A evidência deveria resolver para u0003");
        assertTrue(Files.exists(output), "queries_resolved.json deveria ter sido gravado");
    }

    @Test
    void evidenciaSemNenhumaUnidadeFalhaMostrandoOTrecho() throws IOException {
        writeQuery("texto que não existe na lei");
        GoldenValidationException e = assertThrows(GoldenValidationException.class, this::run,
                "Deveria falhar quando o trecho não existe");
        assertTrue(e.getMessage().contains("texto que não existe na lei"), "A mensagem deveria mostrar o trecho");
        assertFalse(Files.exists(output), "Não deveria gravar o arquivo resolvido em caso de falha");
    }

    @Test
    void evidenciaEmMaisDeUmaUnidadeFalhaListandoCandidatas() throws IOException {
        writeQuery("O tratamento de dados exige");
        GoldenValidationException e = assertThrows(GoldenValidationException.class, this::run,
                "Deveria falhar quando o trecho é ambíguo");
        assertTrue(e.getMessage().contains("u0003") && e.getMessage().contains("u0004"),
                "A mensagem deveria listar u0003 e u0004");
    }

    @Test
    void evidenciaApenasEmTituloFalha() throws IOException {
        writeQuery("Dados sensíveis");
        GoldenValidationException e = assertThrows(GoldenValidationException.class, this::run,
                "Títulos não podem ser evidência");
        assertTrue(e.getMessage().contains("título"), "A mensagem deveria citar título");
    }

    @Test
    void normalizacaoUnificaAspasTravessoesEEspacos() throws IOException {
        writeQuery("o \"DADO pessoal\"  -  de qualquer");
        List<GoldQuery> result = run();
        assertEquals(List.of("u0002"), result.get(0).evidenceUnitIds(),
                "Aspas, travessão e espaços diferentes deveriam ainda casar com u0002");
    }
}
