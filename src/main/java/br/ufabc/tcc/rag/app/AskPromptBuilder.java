package br.ufabc.tcc.rag.app;

import java.util.List;

public final class AskPromptBuilder {

    public record Source(int number, String chunkId, String articles, String text) {
    }

    static final String SYSTEM_PROMPT = "Você responde perguntas sobre a Lei Geral de Proteção de Dados (Lei 13.709/2018). "
            + "Responda apenas com base nos trechos fornecidos pelo usuário. "
            + "Se a resposta não estiver nos trechos, diga claramente que os trechos não contêm a resposta, "
            + "sem completar com conhecimento externo. "
            + "Cite o número do trecho e o artigo em que se apoia.";

    private static final int CHARS_PER_TOKEN = 3;
    private static final double CONTEXT_WARNING_RATIO = 0.8;

    private AskPromptBuilder() {
    }

    public static String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    public static String userPrompt(List<Source> sources, String question) {
        StringBuilder prompt = new StringBuilder("Trechos:\n\n");
        for (Source source : sources) {
            prompt.append('[').append(source.number()).append("] (").append(source.articles()).append(")\n")
                    .append(source.text()).append("\n\n");
        }
        prompt.append("Pergunta: ").append(question);
        return prompt.toString();
    }

    public static int estimateTokens(String systemPrompt, String userPrompt) {
        int characters = systemPrompt.length() + userPrompt.length();
        return (characters + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    public static boolean nearContextLimit(int estimatedTokens, int contextLimit) {
        return estimatedTokens > CONTEXT_WARNING_RATIO * contextLimit;
    }
}
