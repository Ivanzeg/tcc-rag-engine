package br.ufabc.tcc.rag.adapters.chat;

import br.ufabc.tcc.rag.core.port.ChatProvider;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.util.Locale;

public final class OpenAiChatProvider implements ChatProvider {

    private final ChatModel model;
    private final String modelName;
    private final double temperature;

    public OpenAiChatProvider(String apiKey, String modelName, int timeoutSeconds, double temperature) {
        this(OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .temperature(temperature)
                .build(), modelName, temperature);
    }

    public OpenAiChatProvider(ChatModel model, String modelName, double temperature) {
        this.model = model;
        this.modelName = modelName;
        this.temperature = temperature;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        try {
            String text = model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                    .aiMessage().text();
            return text == null ? "" : text.strip();
        } catch (RuntimeException e) {
            String message = String.valueOf(e.getMessage());
            if (message.toLowerCase(Locale.ROOT).contains("temperature")) {
                throw new IllegalStateException("O modelo " + modelName + " recusou temperature=" + temperature
                        + ". Ajuste openai.chat.model ou chat.temperature; não há tentativa sem o parâmetro: "
                        + message, e);
            }
            throw new IllegalStateException("Falha ao gerar a resposta na OpenAI (modelo " + modelName + "): "
                    + message, e);
        }
    }
}
