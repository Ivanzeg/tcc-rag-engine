package br.ufabc.tcc.rag.adapters.chat;

import br.ufabc.tcc.rag.core.port.ChatProvider;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;

public final class OllamaChatProvider implements ChatProvider {

    private final ChatModel model;
    private final String modelName;

    public OllamaChatProvider(String baseUrl, String modelName, int numCtx, int timeoutSeconds, double temperature) {
        this(OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .numCtx(numCtx)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .temperature(temperature)
                .build(), modelName);
    }

    public OllamaChatProvider(ChatModel model, String modelName) {
        this.model = model;
        this.modelName = modelName;
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        try {
            String raw = model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                    .aiMessage().text();
            return ThinkTagStripper.strip(raw == null ? "" : raw);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Falha ao gerar a resposta no Ollama (modelo " + modelName
                    + "). Confira se o servidor está ativo e se o modelo foi baixado: " + e.getMessage(), e);
        }
    }
}
