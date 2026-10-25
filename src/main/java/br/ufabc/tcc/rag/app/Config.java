package br.ufabc.tcc.rag.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class Config {

    private static final String DB_PASSWORD_ENV = "DB_PASSWORD";
    private static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";

    private static final double DEFAULT_CHAT_TEMPERATURE = 0.0;

    private final Properties properties;

    private Config(Properties properties) {
        this.properties = properties;
    }

    public static Config load(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Arquivo de configuração não encontrado: " + path
                    + ". Copie config.example.properties para config.properties e preencha.");
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o arquivo de configuração: " + path, e);
        }
        Config config = new Config(properties);
        config.validateRequired();
        return config;
    }

    private void validateRequired() {
        List<String> missing = Arrays.stream(requiredKeys())
                .filter(key -> raw(key) == null)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Chaves obrigatórias ausentes em config.properties: "
                    + String.join(", ", missing));
        }
    }

    private static String[] requiredKeys() {
        return new String[] {
                "db.url", "db.user",
                "pdf.path", "pdf.headerFooterThreshold",
                "openai.embedding.model",
                "chat.provider",
                "ollama.baseUrl", "ollama.model", "ollama.numCtx", "ollama.timeoutSeconds",
                "experiment.k", "experiment.strategies",
                "semantic.k", "semantic.maxWords",
                "adaptive.maxWords", "adaptive.microHeaders",
                "ask.strategy"
        };
    }

    private String raw(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String required(String key) {
        String value = raw(key);
        if (value == null) {
            throw new IllegalStateException("Chave obrigatória ausente em config.properties: " + key);
        }
        return value;
    }

    private int requiredInt(String key) {
        String value = required(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("A chave " + key + " deve ser um número inteiro.");
        }
    }

    private double requiredDouble(String key) {
        String value = required(key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("A chave " + key + " deve ser um número decimal.");
        }
    }

    private boolean requiredBoolean(String key) {
        String value = required(key);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalStateException("A chave " + key + " deve ser true ou false.");
        }
        return Boolean.parseBoolean(value);
    }

    private static String environment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente ausente: " + name);
        }
        return value;
    }

    public String dbUrl() {
        return required("db.url");
    }

    public String dbUser() {
        return required("db.user");
    }

    public String dbPassword() {
        return environment(DB_PASSWORD_ENV);
    }

    public Path pdfPath() {
        return Path.of(required("pdf.path"));
    }

    public int pdfStartPage() {
        return requiredInt("pdf.startPage");
    }

    public int pdfEndPage() {
        return requiredInt("pdf.endPage");
    }

    public double pdfHeaderFooterThreshold() {
        return requiredDouble("pdf.headerFooterThreshold");
    }

    public String openAiEmbeddingModel() {
        return required("openai.embedding.model");
    }

    public String openAiChatModel() {
        return required("openai.chat.model");
    }

    public String openAiApiKey() {
        return environment(OPENAI_API_KEY_ENV);
    }

    public String chatProvider() {
        return required("chat.provider");
    }

    public double chatTemperature() {
        if (raw("chat.temperature") == null) {
            return DEFAULT_CHAT_TEMPERATURE;
        }
        double value = requiredDouble("chat.temperature");
        if (value < 0.0 || value > 2.0) {
            throw new IllegalStateException("A chave chat.temperature deve estar entre 0 e 2.");
        }
        return value;
    }

    public String ollamaBaseUrl() {
        return required("ollama.baseUrl");
    }

    public String ollamaModel() {
        return required("ollama.model");
    }

    public int ollamaNumCtx() {
        return requiredInt("ollama.numCtx");
    }

    public int ollamaTimeoutSeconds() {
        return requiredInt("ollama.timeoutSeconds");
    }

    public int experimentK() {
        return requiredInt("experiment.k");
    }

    public List<String> experimentStrategies() {
        return Arrays.stream(required("experiment.strategies").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public int semanticK() {
        return requiredInt("semantic.k");
    }

    public int semanticMaxWords() {
        return requiredInt("semantic.maxWords");
    }

    public int adaptiveMaxWords() {
        return requiredInt("adaptive.maxWords");
    }

    public boolean adaptiveMicroHeaders() {
        return requiredBoolean("adaptive.microHeaders");
    }

    public String askStrategy() {
        return required("ask.strategy");
    }
}