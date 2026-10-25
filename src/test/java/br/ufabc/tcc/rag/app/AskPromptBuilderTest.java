package br.ufabc.tcc.rag.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufabc.tcc.rag.app.AskPromptBuilder.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class AskPromptBuilderTest {

    private final List<Source> sources = List.of(
            new Source(1, "adaptive_c0001", "Art. 1o", "texto um"),
            new Source(2, "adaptive_c0002", "Art. 5o, Art. 6o", "texto dois"));

    @Test
    void sistemaMandaResponderApenasComOsTrechos() {
        String system = AskPromptBuilder.systemPrompt();
        assertTrue(system.contains("apenas com base nos trechos"));
        assertTrue(system.contains("não estiver nos trechos"));
    }

    @Test
    void usuarioTemTrechosNumeradosComArtigoEPergunta() {
        String user = AskPromptBuilder.userPrompt(sources, "O que é dado pessoal?");
        assertTrue(user.contains("[1] (Art. 1o)\ntexto um"));
        assertTrue(user.contains("[2] (Art. 5o, Art. 6o)\ntexto dois"));
        assertTrue(user.endsWith("Pergunta: O que é dado pessoal?"));
        assertTrue(user.indexOf("[1]") < user.indexOf("[2]"));
    }

    @Test
    void estimaTokensComoCaracteresDivididosPorTres() {
        assertEquals(3, AskPromptBuilder.estimateTokens("123456", "789"));
        assertEquals(4, AskPromptBuilder.estimateTokens("1234567", "890"));
        assertEquals(4, AskPromptBuilder.estimateTokens("12345678", "90"));
    }

    @Test
    void avisaSoQuandoPassaDeOitentaPorCentoDoContexto() {
        assertFalse(AskPromptBuilder.nearContextLimit(6553, 8192));
        assertTrue(AskPromptBuilder.nearContextLimit(6554, 8192));
    }
}
