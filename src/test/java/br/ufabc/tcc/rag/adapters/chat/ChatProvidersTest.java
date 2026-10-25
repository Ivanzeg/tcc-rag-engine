package br.ufabc.tcc.rag.adapters.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class ChatProvidersTest {

    private static ChatModel replying(String text) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
            }
        };
    }

    private static ChatModel failing(String message) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw new RuntimeException(message);
            }
        };
    }

    @Test
    void ollamaRemoveBlocoThinkDaSaida() {
        OllamaChatProvider provider = new OllamaChatProvider(replying("<think>pensando</think>Resposta"), "qwen3:8b");
        assertEquals("Resposta", provider.generate("sistema", "usuário"));
        assertEquals("ollama", provider.name());
    }

    @Test
    void openAiDevolveTextoSemAlterarConteudo() {
        OpenAiChatProvider provider = new OpenAiChatProvider(replying("Resposta"), "modelo-x", 0.0);
        assertEquals("Resposta", provider.generate("sistema", "usuário"));
        assertEquals("openai", provider.name());
    }

    @Test
    void openAiTraduzRecusaDeTemperaturaSemFallback() {
        OpenAiChatProvider provider = new OpenAiChatProvider(
                failing("Unsupported value: 'temperature' does not support 0.0"), "modelo-x", 0.0);
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> provider.generate("sistema", "usuário"));
        assertTrue(erro.getMessage().contains("recusou temperature=0.0"));
        assertTrue(erro.getMessage().contains("modelo-x"));
        assertInstanceOf(RuntimeException.class, erro.getCause());
    }

    @Test
    void openAiTraduzOutraFalhaEmPortugues() {
        OpenAiChatProvider provider = new OpenAiChatProvider(failing("timeout"), "modelo-x", 0.0);
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> provider.generate("sistema", "usuário"));
        assertTrue(erro.getMessage().startsWith("Falha ao gerar a resposta na OpenAI"));
    }

    @Test
    void ollamaTraduzFalhaEmPortugues() {
        OllamaChatProvider provider = new OllamaChatProvider(failing("conexão recusada"), "qwen3:8b");
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> provider.generate("sistema", "usuário"));
        assertTrue(erro.getMessage().startsWith("Falha ao gerar a resposta no Ollama"));
    }
}
