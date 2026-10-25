package br.ufabc.tcc.rag.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.ufabc.tcc.rag.core.port.ChatProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChatProviderFactoryTest {

    @TempDir
    Path dir;

    private static ChatProvider fake(String name) {
        return new ChatProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String generate(String systemPrompt, String userPrompt) {
                return "";
            }
        };
    }

    private Config config(String provider, String extra) throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, String.join("\n",
                "db.url=jdbc:postgresql://localhost:5432/x",
                "db.user=u",
                "pdf.path=x.pdf",
                "pdf.headerFooterThreshold=0.40",
                "openai.embedding.model=text-embedding-3-small",
                "openai.chat.model=modelo-x",
                "chat.provider=" + provider,
                "ollama.baseUrl=http://localhost:11434",
                "ollama.model=qwen3:8b",
                "ollama.numCtx=8192",
                "ollama.timeoutSeconds=300",
                "experiment.k=5",
                "experiment.strategies=fixed",
                "semantic.k=10",
                "semantic.maxWords=500",
                "adaptive.maxWords=500",
                "adaptive.microHeaders=false",
                "ask.strategy=adaptive",
                extra));
        return Config.load(file);
    }

    @Test
    void escolheOllamaPeloNome() {
        ChatProvider ollama = fake("ollama");
        ChatProvider openAi = fake("openai");
        assertSame(ollama, ChatProviderFactory.choose("ollama", () -> ollama, () -> openAi));
    }

    @Test
    void escolheOpenAiPeloNomeSemDependerDeCaixa() {
        ChatProvider ollama = fake("ollama");
        ChatProvider openAi = fake("openai");
        assertSame(openAi, ChatProviderFactory.choose("OpenAI", () -> ollama, () -> openAi));
    }

    @Test
    void naoConstroiOProvedorNaoEscolhido() {
        ChatProvider ollama = fake("ollama");
        assertSame(ollama, ChatProviderFactory.choose("ollama", () -> ollama, () -> {
            throw new AssertionError("o provedor openai não deveria ser construído");
        }));
    }

    @Test
    void valorDesconhecidoFalhaEmPortugues() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> ChatProviderFactory.choose("gemini", () -> fake("a"), () -> fake("b")));
        assertTrue(erro.getMessage().contains("chat.provider desconhecido: gemini"));
    }

    @Test
    void temperaturaPadraoEZero() throws IOException {
        assertEquals(0.0, config("ollama", "").chatTemperature());
    }

    @Test
    void temperaturaForaDaFaixaFalha() throws IOException {
        Config config = config("ollama", "chat.temperature=3");
        assertThrows(IllegalStateException.class, config::chatTemperature);
    }

    @Test
    void linhaDeConfiguracaoTrazMesmaTemperaturaNosDoisProvedores() throws IOException {
        String ollama = ChatProviderFactory.configLine(config("ollama", "chat.temperature=0.0"));
        String openAi = ChatProviderFactory.configLine(config("openai", "chat.temperature=0.0"));
        assertTrue(ollama.contains("chat.provider=ollama"));
        assertTrue(ollama.contains("ollama.model=qwen3:8b"));
        assertTrue(ollama.contains("ollama.numCtx=8192"));
        assertTrue(ollama.contains("chat.temperature=0.0"));
        assertTrue(openAi.contains("chat.provider=openai"));
        assertTrue(openAi.contains("openai.chat.model=modelo-x"));
        assertTrue(openAi.contains("chat.temperature=0.0"));
    }
}
