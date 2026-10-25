package br.ufabc.tcc.rag.app;

import br.ufabc.tcc.rag.adapters.chat.OllamaChatProvider;
import br.ufabc.tcc.rag.adapters.chat.OpenAiChatProvider;
import br.ufabc.tcc.rag.core.port.ChatProvider;
import java.util.Locale;
import java.util.function.Supplier;

public final class ChatProviderFactory {

    private ChatProviderFactory() {
    }

    public static ChatProvider create(Config config) {
        return choose(config.chatProvider(), () -> ollama(config), () -> openAi(config));
    }

    static ChatProvider choose(String provider, Supplier<ChatProvider> ollama, Supplier<ChatProvider> openAi) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "ollama" -> ollama.get();
            case "openai" -> openAi.get();
            default -> throw new IllegalStateException("chat.provider desconhecido: " + provider
                    + ". Os valores aceitos são ollama e openai.");
        };
    }

    public static String configLine(Config config) {
        String provider = config.chatProvider().toLowerCase(Locale.ROOT);
        String common = "chat.provider=" + provider;
        String temperature = "chat.temperature=" + config.chatTemperature();
        return switch (provider) {
            case "ollama" -> common + "; ollama.model=" + config.ollamaModel()
                    + "; ollama.numCtx=" + config.ollamaNumCtx() + "; " + temperature;
            case "openai" -> common + "; openai.chat.model=" + config.openAiChatModel() + "; " + temperature;
            default -> throw new IllegalStateException("chat.provider desconhecido: " + config.chatProvider()
                    + ". Os valores aceitos são ollama e openai.");
        };
    }

    private static ChatProvider ollama(Config config) {
        return new OllamaChatProvider(config.ollamaBaseUrl(), config.ollamaModel(), config.ollamaNumCtx(),
                config.ollamaTimeoutSeconds(), config.chatTemperature());
    }

    private static ChatProvider openAi(Config config) {
        return new OpenAiChatProvider(config.openAiApiKey(), config.openAiChatModel(),
                config.ollamaTimeoutSeconds(), config.chatTemperature());
    }
}
