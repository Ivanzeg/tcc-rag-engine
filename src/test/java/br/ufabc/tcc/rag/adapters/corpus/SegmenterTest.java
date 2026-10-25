package br.ufabc.tcc.rag.adapters.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufabc.tcc.rag.core.domain.Corpus;
import br.ufabc.tcc.rag.core.domain.Unit;
import br.ufabc.tcc.rag.core.domain.UnitType;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SegmenterTest {

    private static final Path PDF = Path.of("data", "raw", "lgpd_senado_2ed.pdf");
    private static final int START_PAGE = 8;
    private static final int END_PAGE = 47;
    private static final double THRESHOLD = 0.40;

    private static CorpusBuild build;
    private static Corpus corpus;

    @BeforeAll
    static void buildCorpus() {
        build = new CorpusPipeline().build(PDF, START_PAGE, END_PAGE, THRESHOLD);
        corpus = build.corpus();
    }

    @Test
    void nenhumaUnidadeVazia() {
        for (Unit unit : corpus.units()) {
            assertFalse(unit.text().isBlank(), "Unidade vazia: " + unit.id());
        }
    }

    @Test
    void nenhumaUnidadeVetada() {
        for (Unit unit : corpus.units()) {
            assertFalse(unit.text().toLowerCase().contains("(vetad"), "Unidade com dispositivo vetado: " + unit.id());
        }
    }

    @Test
    void nenhumaUnidadeComConteudoVazio() {
        Pattern label = Pattern.compile("^(?:Art\\.\\s*\\d+[ºo°]?(?:-[A-Z])?|§\\s*\\d+[ºo°]?(?:-[A-Z])?"
                + "|Parágrafo\\s+único|[IVXLCDM]+(?:-[A-Z])?\\s*-|[a-z]\\))[\\s.]*");
        for (Unit unit : corpus.units()) {
            if (unit.type() != UnitType.PROVISION) {
                continue;
            }
            String content = label.matcher(unit.text()).replaceFirst("");
            assertFalse(content.matches("^[\\p{Punct}\\s]*$"), "Unidade sem conteúdo: " + unit.id() + " " + unit.text());
        }
    }

    @Test
    void semAnotacaoRedacaoDada() {
        assertFalse(corpus.canonicalText().contains("Redação dada"), "O texto canônico ainda contém 'Redação dada'.");
    }

    @Test
    void codificacaoPreservada() {
        String text = corpus.canonicalText();
        assertTrue(text.contains("Proteção de Dados"), "O texto canônico deveria conter 'Proteção de Dados'.");
        assertFalse(Pattern.compile("Ã[\\u0080-\\u00BF]").matcher(text).find(),
                "O texto canônico contém sequência típica de codificação quebrada iniciada por 'Ã'.");
        assertFalse(text.contains("�"), "O texto canônico contém o caractere de substituição.");
    }

    @Test
    void nenhumaUnidadeSoComDigitos() {
        for (Unit unit : corpus.units()) {
            assertFalse(unit.text().matches("[\\d\\s]+"), "Unidade só com dígitos: " + unit.id());
        }
    }

    @Test
    void cabecalhoDetectadoNaoSobrevive() {
        assertFalse(build.report().headerFooterForms.isEmpty(), "Nenhum cabeçalho/rodapé foi detectado.");
        for (String line : corpus.canonicalText().split("\n")) {
            String form = LineCleaner.headerFooterForm(line);
            assertFalse(build.report().headerFooterForms.containsKey(form),
                    "Linha com a forma de cabeçalho detectada: " + line);
        }
        String text = corpus.canonicalText();
        for (String fragment : List.of("Lei no 13.709/2018 9", "Lei no 13.709/2018 11",
                "10 Lei Geral de Proteção de Dados Pessoais", "12 Lei Geral de Proteção de Dados Pessoais")) {
            assertFalse(text.contains(fragment), "O texto canônico contém resíduo de cabeçalho: " + fragment);
        }
    }

    @Test
    void existeUnidadeIniciadaPorArt1() {
        boolean found = corpus.units().stream().anyMatch(u -> u.text().matches("^Art\\. 1[ºo]\\s.*"));
        assertTrue(found, "Nenhuma unidade começa com 'Art. 1'.");
    }

    @Test
    void somaDasUnidadesReconstroiTextoCanonico() {
        List<String> texts = corpus.units().stream().map(Unit::text).toList();
        assertEquals(corpus.canonicalText(), String.join("\n\n", texts));
        for (Unit unit : corpus.units()) {
            assertEquals(unit.text(), corpus.canonicalText().substring(unit.start(), unit.end()),
                    "Posições inconsistentes na unidade " + unit.id());
        }
    }

    @Test
    void primeiraUnidadeEhCapituloI() {
        Unit first = corpus.units().get(0);
        assertEquals(UnitType.HEADING, first.type());
        assertTrue(first.text().startsWith("CAPÍTULO I"), "Primeira unidade: " + first.text());
    }

    @Test
    void fechoDaLeiFoiDescartado() {
        for (Unit unit : corpus.units()) {
            assertFalse(unit.text().contains("Brasília, 14 de agosto"), "Fecho na unidade " + unit.id());
        }
    }

    @Test
    void ultimoDispositivoPertenceAoArt65() {
        List<Unit> provisions = corpus.units().stream().filter(u -> u.type() == UnitType.PROVISION).toList();
        Unit last = provisions.get(provisions.size() - 1);
        assertTrue(last.article().startsWith("Art. 65"), "Artigo do último dispositivo: " + last.article());
        Unit lastArticle = provisions.stream()
                .filter(u -> u.text().startsWith("Art. ")).reduce((a, b) -> b).orElseThrow();
        assertTrue(lastArticle.text().startsWith("Art. 65"), "Último artigo: " + lastArticle.text());
    }
}
